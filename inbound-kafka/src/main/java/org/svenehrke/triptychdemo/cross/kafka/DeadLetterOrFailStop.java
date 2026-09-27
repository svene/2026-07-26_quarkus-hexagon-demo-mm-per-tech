package org.svenehrke.triptychdemo.cross.kafka;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecord;
import io.smallrye.reactive.messaging.kafka.KafkaConnectorIncomingConfiguration;
import io.smallrye.reactive.messaging.kafka.KafkaConsumer;
import io.smallrye.reactive.messaging.kafka.fault.KafkaDeadLetterQueue;
import io.smallrye.reactive.messaging.kafka.fault.KafkaFailureHandler;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import java.util.function.BiConsumer;

/**
 * Failure strategy {@code dead-letter-or-fail-stop}: a message that can never be processed goes to
 * the dead-letter topic, any other failure stops the channel (see
 * docs/architecture/kafka-unprocessable-messages.md).
 * <ul>
 *   <li>Dead letter: undeserializable records and {@link UnprocessableMessageException}. Retrying
 *       them gives the same failure every time.</li>
 *   <li>Fail-stop: everything else, i.e. transient failures such as a database that is down, which
 *       the receivers' {@code @Retry} couldn't overcome. The offset stays uncommitted, so the message
 *       is processed again after a restart, and the next messages aren't sent to the DLQ as well.</li>
 * </ul>
 * Both handlers are SmallRye's own, so the DLQ configuration ({@code dead-letter-queue.*}) works
 * unchanged. This class extends {@link KafkaDeadLetterQueue} only because SmallRye sends
 * undeserializable records to the failure handler (instead of to the receiver as {@code null}) only
 * if the handler is an {@code instanceof KafkaDeadLetterQueue}; it overrides everything that uses the
 * inherited state.
 */
public class DeadLetterOrFailStop extends KafkaDeadLetterQueue {

    public static final String STRATEGY = "dead-letter-or-fail-stop";

    private final KafkaFailureHandler deadLetter;
    private final KafkaFailureHandler failStop;

    DeadLetterOrFailStop(String channel, KafkaFailureHandler deadLetter, KafkaFailureHandler failStop) {
        super(channel, null, null, null);
        this.deadLetter = deadLetter;
        this.failStop = failStop;
    }

    @ApplicationScoped
    @Identifier(STRATEGY)
    public static class Factory implements KafkaFailureHandler.Factory {

        @Inject
        @Identifier(KafkaFailureHandler.Strategy.DEAD_LETTER_QUEUE)
        KafkaFailureHandler.Factory deadLetterFactory;

        @Inject
        @Identifier(KafkaFailureHandler.Strategy.FAIL)
        KafkaFailureHandler.Factory failStopFactory;

        @Override
        public KafkaFailureHandler create(KafkaConnectorIncomingConfiguration config, Vertx vertx,
                KafkaConsumer<?, ?> consumer, BiConsumer<Throwable, Boolean> reportFailure) {
            return new DeadLetterOrFailStop(config.getChannel(),
                deadLetterFactory.create(config, vertx, consumer, reportFailure),
                failStopFactory.create(config, vertx, consumer, reportFailure));
        }
    }

    @Override
    public <K, V> Uni<Void> handle(IncomingKafkaRecord<K, V> record, Throwable reason, Metadata metadata) {
        return isUnprocessable(reason)
            ? deadLetter.handle(record, reason, metadata)
            : failStop.handle(record, reason, metadata);
    }

    @Override
    public void terminate() {
        deadLetter.terminate();
        failStop.terminate();
    }

    private static boolean isUnprocessable(Throwable reason) {
        return reason instanceof RecordDeserializationException
            || reason instanceof UnprocessableMessageException;
    }
}
