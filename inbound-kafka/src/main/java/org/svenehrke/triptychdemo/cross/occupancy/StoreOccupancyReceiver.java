package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Store;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Path;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.svenehrke.triptychdemo.cross.kafka.UnprocessableMessageException;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The stores' checkout systems report their occupancy every few seconds. A snapshot, not an event: a redelivered or
 * outdated one is simply not newer than what is stored, so no inbox is needed. Only invalid messages are
 * audit-logged; a valid one every few seconds per store would drown the audit log.
 */
@ApplicationScoped
public class StoreOccupancyReceiver {

    @Inject
    OccupancyHandler occupancyHandler;
    @Inject
    AuditLogHandler auditLog;

    @Incoming("store-occupancy")
    @RunOnVirtualThread
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(OccupancyMessage message) {
        rejectUnprocessable(message);
        store(message).flatMap(store -> validated(store, message)).ifPresent(occupancyHandler::record);
    }

    // Empty if storeId names no store (audit-logged, then skipped), like any other invalid content.
    private Optional<Store> store(OccupancyMessage message) {
        var store = Locations.storeById(message.storeId());
        if (store.isEmpty()) {
            auditLog.log("StoreOccupancyReceiver: INVALID", "storeId '%s' is not a store".formatted(message.storeId()));
        }
        return store;
    }

    // Empty if the message is invalid (audit-logged, then skipped).
    private Optional<StoreOccupancy> validated(Store store, OccupancyMessage m) {
        return switch (StoreOccupancy.parse(store, m.measuredAt(), m.inside(), m.capacity(), m.queuing(), m.tills(),
            m.tillsBusy(), m.turnedAway())) {
            case ParsedStoreOccupancy.Invalid invalid -> {
                auditLog.log("StoreOccupancyReceiver: INVALID", "%s: %s".formatted(store.id(), invalid.violations().stream()
                    .map(v -> parameterName(v.getPropertyPath()) + " " + v.getMessage()).sorted()
                    .collect(Collectors.joining(", "))));
                yield Optional.empty();
            }
            case StoreOccupancy occupancy -> Optional.of(occupancy);
        };
    }

    // "StoreOccupancy.inside" → "inside": the constructor parameter's name, as in the message
    private static String parameterName(Path path) {
        String name = null;
        for (var node : path) name = node.getName();
        return name;
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(OccupancyMessage message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
        if (message.storeId() == null || message.measuredAt() == null || message.inside() == null
            || message.capacity() == null || message.queuing() == null || message.tills() == null
            || message.tillsBusy() == null || message.turnedAway() == null) {
            throw new UnprocessableMessageException(
                "storeId, measuredAt, inside, capacity, queuing, tills, tillsBusy and turnedAway are required");
        }
    }
}
