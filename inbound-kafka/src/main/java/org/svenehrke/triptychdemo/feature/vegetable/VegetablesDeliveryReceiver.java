package org.svenehrke.triptychdemo.feature.vegetable;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.svenehrke.triptychdemo.cross.kafka.UnprocessableMessageException;

import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class VegetablesDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("vegetables-deliveries")
    @Blocking
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, abortOn = UnprocessableMessageException.class)
    public void receive(RawVegetableDelivery message) {
        rejectUnprocessable(message);
        // Mapping: RawVegetableDelivery -> VegetableDelivery:
        switch (VegetableDelivery.parse(message.productName(), message.quantity())) {
            case ParsedVegetableDelivery.Invalid invalid: {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("VegetablesDeliveryReceiver: VEGETABLE_DELIVERY_RECEIVED",
                    "INVALID: %s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                break;
            }
            case VegetableDelivery vegetableDelivery: {
                auditLog.log("VegetablesDeliveryReceiver: VEGETABLE_DELIVERY_RECEIVED", vegetableDelivery.productName() + " qty=" + vegetableDelivery.quantity());
                inventoryAPI.updateVegetableAmount(vegetableDelivery);
                auditLog.log("VegetablesDeliveryReceiver: VEGETABLE_INVENTORY_UPDATED", vegetableDelivery.productName() + " +" + vegetableDelivery.quantity());
                break;
            }
        }
    }

    // UnprocessableMessageException skips @Retry and sends the message to the dead-letter topic (see DeadLetterOrFailStop).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(RawVegetableDelivery message) {
        if (message == null) {
            throw new UnprocessableMessageException("null payload (tombstone)");
        }
    }
}
