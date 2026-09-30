package org.svenehrke.triptychdemo.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Messages that fail before {@code parse()} (undeserializable, tombstone, broken structure) go to
 * the channel's dead-letter topic with their original bytes, and must not stop the channel: each
 * case sends bad message(s), then a valid one that still has to be processed. Without the DLQ
 * configuration, every one of these cases permanently stops its consumer.
 * <p>
 * The channels and their DLQs are remapped to probe-only topics, so the bad records can't leak
 * into other tests.
 */
@QuarkusTest
@TestProfile(KafkaMalformedMessageTest.ProbeTopics.class)
class KafkaMalformedMessageTest {

    public static class ProbeTopics implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            var config = new HashMap<String, String>();
            for (String channel : List.of("fruit-deliveries", "vegetables-deliveries", "dairy-deliveries", "cashpoint-purchases")) {
                config.put("mp.messaging.incoming.%s.topic".formatted(channel), "probe-" + channel);
                config.put("mp.messaging.incoming.%s.auto.offset.reset".formatted(channel), "earliest");
                config.put("mp.messaging.incoming.%s.dead-letter-queue.topic".formatted(channel), "probe-" + channel + "-dlq");
            }
            return config;
        }
    }

    static KafkaProducer<String, String> producer;
    static String bootstrap;

    @Inject TestAuditLogHelper auditHelper;

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
    void invalid_json_goes_to_dlq_and_channel_keeps_consuming() throws Exception {
        send("probe-fruit-deliveries", "{not json");
        send("probe-fruit-deliveries", """
            {"productName": "Mango", "quantity": 5}""");

        assertProcessed("FruitDeliveryReceiver: FRUIT_DELIVERY_RECEIVED", "Mango qty=5");
        var dead = readDlq("probe-fruit-deliveries-dlq", 1);
        assertDeadLetter(dead.getFirst(), "{not json", "probe-fruit-deliveries");
    }

    @Test
    void wrongly_typed_field_goes_to_dlq_and_channel_keeps_consuming() throws Exception {
        String bad = """
            {"productName": "Carrot", "quantity": "abc"}""";
        send("probe-vegetables-deliveries", bad);
        send("probe-vegetables-deliveries", """
            {"productName": "Leek", "quantity": 5}""");

        assertProcessed("VegetablesDeliveryReceiver: VEGETABLE_DELIVERY_RECEIVED", "Leek qty=5");
        var dead = readDlq("probe-vegetables-deliveries-dlq", 1);
        assertDeadLetter(dead.getFirst(), bad, "probe-vegetables-deliveries");
    }

    @Test
    void tombstone_goes_to_dlq_and_channel_keeps_consuming() throws Exception {
        send("probe-dairy-deliveries", null);
        send("probe-dairy-deliveries", """
            {"productName": "Milk", "quantity": 5}""");

        // The receipt is logged before the tombstone is rejected.
        assertProcessed("DairyDeliveryReceiver: DAIRY_DELIVERY_RECEIVED", "null payload (tombstone)", "Milk qty=5");
        var dead = readDlq("probe-dairy-deliveries-dlq", 1);
        // The DLQ's ObjectMapperSerializer writes the null payload as JSON "null", not as a tombstone.
        assertDeadLetter(dead.getFirst(), "null", "probe-dairy-deliveries");
        assertThat(header(dead.getFirst(), "dead-letter-reason")).isEqualTo("null payload (tombstone)");
    }

    @Test
    void broken_cashpoint_structure_goes_to_dlq_and_channel_keeps_consuming() throws Exception {
        send("probe-cashpoint-purchases", """
            {"items": null}""");
        send("probe-cashpoint-purchases", """
            {"items": [null]}""");
        send("probe-cashpoint-purchases", """
            {"items": [{"productName": "Orange", "quantity": 1}]}""");

        assertProcessed("PurchaseHandler: PURCHASE_PROCESSING", "Orange qty=1");
        // The receipt is logged before the structural checks reject a message.
        assertThat(auditHelper.findEventDetails("CashpointReceiver: PURCHASE_RECEIVED"))
            .containsExactly("items: null", "null", "Orange qty=1");
        var dead = readDlq("probe-cashpoint-purchases-dlq", 2);
        assertThat(dead).extracting(r -> header(r, "dead-letter-reason"))
            .containsExactly("items is required", "items must not contain null entries");
        assertThat(dead).allSatisfy(r ->
            assertThat(header(r, "dead-letter-topic")).isEqualTo("probe-cashpoint-purchases"));
    }

    private void send(String topic, String value) throws Exception {
        producer.send(new ProducerRecord<>(topic, value)).get();
    }

    private void assertProcessed(String event, String... details) {
        await().atMost(15, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails(event)).containsExactly(details));
    }

    private static List<ConsumerRecord<String, String>> readDlq(String topic, int expected) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
            ConsumerConfig.GROUP_ID_CONFIG, "dlq-reader-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            var records = new ArrayList<ConsumerRecord<String, String>>();
            await().atMost(15, SECONDS).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
                return records.size() >= expected;
            });
            return records;
        }
    }

    private static void assertDeadLetter(ConsumerRecord<String, String> record, String rawValue, String sourceTopic) {
        assertThat(record.value()).isEqualTo(rawValue);
        assertThat(header(record, "dead-letter-topic")).isEqualTo(sourceTopic);
        assertThat(header(record, "dead-letter-reason")).isNotBlank();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
