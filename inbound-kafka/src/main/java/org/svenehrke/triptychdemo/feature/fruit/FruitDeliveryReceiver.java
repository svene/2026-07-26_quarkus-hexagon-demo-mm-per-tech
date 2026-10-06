package org.svenehrke.triptychdemo.feature.fruit;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.inventory.InventoryHandler;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.svenehrke.triptychdemo.cross.kafka.UnprocessableMessageException;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class FruitDeliveryReceiver {

    @Inject
    InventoryHandler inventoryHandler;
    @Inject
    AuditLogHandler auditLog;

    @Incoming("fruit-deliveries")
    @RunOnVirtualThread
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(RawFruitDelivery message) {
        logReceived(message);
        validated(message).ifPresent(inventoryHandler::updateFruitAmount);
    }

    private void logReceived(RawFruitDelivery message) {
        auditLog.log("FruitDeliveryReceiver: FRUIT_DELIVERY_RECEIVED",
            message == null ? "null payload (tombstone)" : message.productName() + " qty=" + message.quantity());
    }

    // Empty if the message is invalid (audit-logged, then skipped).
    private Optional<FruitDelivery> validated(RawFruitDelivery message) {
        rejectUnprocessable(message);
        // Mapping: RawFruitDelivery -> FruitDelivery:
        return switch (FruitDelivery.parse(message.productName(), message.quantity())) {
            case ParsedFruitDelivery.Invalid invalid -> {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("FruitDeliveryReceiver: INVALID",
                    "%s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                yield Optional.empty();
            }
            case FruitDelivery fruitDelivery -> Optional.of(fruitDelivery);
        };
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(RawFruitDelivery message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
    }
}
