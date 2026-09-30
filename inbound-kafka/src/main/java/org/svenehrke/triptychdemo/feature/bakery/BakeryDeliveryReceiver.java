package org.svenehrke.triptychdemo.feature.bakery;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.inventory.InventoryHandler;

import io.smallrye.reactive.messaging.annotations.Blocking;
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
public class BakeryDeliveryReceiver {

    @Inject
    InventoryHandler inventoryHandler;
    @Inject
    AuditLogHandler auditLog;

    @Incoming("bakery-deliveries")
    @Blocking
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(RawBakeryDelivery message) {
        logReceived(message);
        validated(message).ifPresent(inventoryHandler::updateBakeryAmount);
    }

    private void logReceived(RawBakeryDelivery message) {
        auditLog.log("BakeryDeliveryReceiver: BAKERY_DELIVERY_RECEIVED",
            message == null ? "null payload (tombstone)" : message.productName() + " qty=" + message.quantity());
    }

    // Empty if the message is invalid (audit-logged, then skipped).
    private Optional<BakeryDelivery> validated(RawBakeryDelivery message) {
        rejectUnprocessable(message);
        // Mapping: RawBakeryDelivery -> BakeryDelivery:
        return switch (BakeryDelivery.parse(message.productName(), message.quantity())) {
            case ParsedBakeryDelivery.Invalid invalid -> {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("BakeryDeliveryReceiver: INVALID",
                    "%s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                yield Optional.empty();
            }
            case BakeryDelivery bakeryDelivery -> Optional.of(bakeryDelivery);
        };
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(RawBakeryDelivery message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
    }
}
