package org.svenehrke.triptychdemo.external.outbound.kafka;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A supplier's lead time: the stubs deliver an order {@code supplier-stub.lead-time} ± 20% after it was placed. The
 * delivery is scheduled on Mutiny's worker pool, so the thread that received the order (HTTP, SOAP or Kafka consumer)
 * returns right away instead of waiting for it. Each external module has its own copy, as they stand for independent
 * suppliers. The carrier stub uses {@link #later} with its own transit time. Looked up per delivery, so a test can
 * switch it without its own Quarkus instance.
 */
@ApplicationScoped
public class LeadTime {

    @ConfigProperty(name = "supplier-stub.lead-time", defaultValue = "0s")
    Supplier<Duration> leadTime;

    public void deliverLater(Runnable delivery) {
        later(leadTime.get(), delivery);
    }

    /** Runs {@code action} {@code delay} ± 20% from now, on Mutiny's worker pool. */
    public void later(Duration delay, Runnable action) {
        Infrastructure.getDefaultWorkerPool().schedule(() -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                Log.error("Delayed stub action failed", e);
            }
        }, jittered(delay), MILLISECONDS);
    }

    private static long jittered(Duration delay) {
        long millis = delay.toMillis();
        long jitter = millis / 5;
        // Quarkus' worker pool schedules on Vert.x timers, which reject delays below 1 ms
        return Math.max(1, millis - jitter + ThreadLocalRandom.current().nextLong(2 * jitter + 1));
    }
}
