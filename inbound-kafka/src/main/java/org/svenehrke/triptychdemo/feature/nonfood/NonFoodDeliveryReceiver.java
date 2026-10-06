package org.svenehrke.triptychdemo.feature.nonfood;

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
public class NonFoodDeliveryReceiver {

    @Inject
    InventoryHandler inventoryHandler;
    @Inject
    AuditLogHandler auditLog;

    @Incoming("nonfood-deliveries")
    @RunOnVirtualThread
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(RawNonFoodDelivery message) {
        logReceived(message);
        validated(message).ifPresent(inventoryHandler::updateNonFoodAmount);
    }

    private void logReceived(RawNonFoodDelivery message) {
        auditLog.log("NonFoodDeliveryReceiver: NON_FOOD_DELIVERY_RECEIVED",
            message == null ? "null payload (tombstone)" : message.productName() + " qty=" + message.quantity());
    }

    // Empty if the message is invalid (audit-logged, then skipped).
    private Optional<NonFoodDelivery> validated(RawNonFoodDelivery message) {
        rejectUnprocessable(message);
        // Mapping: RawNonFoodDelivery -> NonFoodDelivery:
        return switch (NonFoodDelivery.parse(message.productName(), message.quantity())) {
            case ParsedNonFoodDelivery.Invalid invalid -> {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("NonFoodDeliveryReceiver: INVALID",
                    "%s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                yield Optional.empty();
            }
            case NonFoodDelivery nonFoodDelivery -> Optional.of(nonFoodDelivery);
        };
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(RawNonFoodDelivery message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
    }
}
