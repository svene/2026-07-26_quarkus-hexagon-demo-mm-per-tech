package org.svenehrke.triptychdemo.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderService;
import org.svenehrke.triptychdemo.cross.products.ProductType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/**
 * A failure that isn't the message's fault (here: the inventory database throwing) is retried by the
 * receiver's {@code @Retry}. If that doesn't help, the channel stops without committing the offset
 * (failure-strategy=dead-letter-or-fail-stop), so the message is processed again after a restart;
 * it must NOT go to the dead-letter topic.
 * <p>
 * Each test uses its own channel, because a stopped channel stays stopped for the rest of this
 * Quarkus instance. The channels are remapped to probe-only topics, as in
 * {@link KafkaMalformedMessageTest}.
 */
@QuarkusTest
@TestProfile(KafkaTransientFailureTest.ProbeTopics.class)
class KafkaTransientFailureTest {

    static final String VEGETABLES_GROUP = "transient-probe-vegetables";

    public static class ProbeTopics implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            var config = new HashMap<String, String>();
            for (String channel : List.of("fruit-deliveries", "vegetables-deliveries")) {
                config.put("mp.messaging.incoming.%s.topic".formatted(channel), "transient-" + channel);
                config.put("mp.messaging.incoming.%s.auto.offset.reset".formatted(channel), "earliest");
                config.put("mp.messaging.incoming.%s.dead-letter-queue.topic".formatted(channel), "transient-" + channel + "-dlq");
            }
            config.put("mp.messaging.incoming.vegetables-deliveries.group.id", VEGETABLES_GROUP);
            return config;
        }
    }

    static KafkaProducer<String, String> producer;
    static String bootstrap;

    @Inject TestAuditLogHelper auditHelper;
    @InjectSpy SupplierOrderService supplierOrderService;

    @BeforeAll
    static void createProducer() {
        bootstrap = ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
        producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @AfterAll
    static void closeProducer() {
        producer.close();
    }

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            auditHelper.clearAuditLog();
            return auditHelper.isEmpty();
        });
    }

    @Test
    void transient_failure_is_retried() throws Exception {
        doThrow(new IllegalStateException("database down"))
            .doCallRealMethod()
            .when(supplierOrderService).receiveDelivery(anyString(), eq(ProductType.FRUIT), anyInt());

        send("transient-fruit-deliveries", """
            {"productName": "Mango", "quantity": 5}""");

        await().atMost(15, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("InventoryHandler: FRUIT_INVENTORY_UPDATED"))
                .containsExactly("dc: Mango +5"));
        // @Retry runs the whole receive() again, so the receipt is logged once per attempt.
        assertThat(auditHelper.findEventDetails("FruitDeliveryReceiver: FRUIT_DELIVERY_RECEIVED"))
            .containsExactly("Mango qty=5", "Mango qty=5");
        assertThat(countRecords("transient-fruit-deliveries-dlq")).isZero();
    }

    @Test
    void persistent_failure_stops_the_channel_without_dead_lettering() throws Exception {
        doThrow(new IllegalStateException("database down"))
            .when(supplierOrderService).receiveDelivery(anyString(), eq(ProductType.VEGETABLE), anyInt());

        send("transient-vegetables-deliveries", """
            {"productName": "Leek", "quantity": 5}""");

        // 1 attempt + 3 retries, then the channel stops.
        await().atMost(15, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("VegetablesDeliveryReceiver: VEGETABLE_DELIVERY_RECEIVED"))
                .hasSize(4));

        // The database "recovers", but the stopped channel doesn't consume the next message.
        doCallRealMethod().when(supplierOrderService).receiveDelivery(anyString(), eq(ProductType.VEGETABLE), anyInt());
        send("transient-vegetables-deliveries", """
            {"productName": "Carrot", "quantity": 5}""");
        await().during(2, SECONDS).atMost(3, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("VegetablesDeliveryReceiver: VEGETABLE_DELIVERY_RECEIVED"))
                .containsOnly("Leek qty=5"));

        assertThat(countRecords("transient-vegetables-deliveries-dlq")).isZero();
        // Nothing committed: after a restart, the consumer group starts again at Leek.
        try (var admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            var committed = admin.listConsumerGroupOffsets(VEGETABLES_GROUP)
                .partitionsToOffsetAndMetadata().get();
            assertThat(committed.values()).allSatisfy(offset ->
                assertThat(offset == null ? 0 : offset.offset()).isZero());
        }
    }

    private void send(String topic, String value) throws Exception {
        producer.send(new ProducerRecord<>(topic, value)).get();
    }

    /** The topic's end offset: instant, where polling an empty topic waits for the whole poll timeout. */
    private static long countRecords(String topic) throws Exception {
        try (var admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            if (!admin.listTopics().names().get().contains(topic)) return 0;
            var partitions = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic).partitions().stream()
                .collect(Collectors.toMap(p -> new TopicPartition(topic, p.partition()), p -> OffsetSpec.latest()));
            return admin.listOffsets(partitions).all().get().values().stream()
                .mapToLong(ListOffsetsResult.ListOffsetsResultInfo::offset).sum();
        }
    }
}
