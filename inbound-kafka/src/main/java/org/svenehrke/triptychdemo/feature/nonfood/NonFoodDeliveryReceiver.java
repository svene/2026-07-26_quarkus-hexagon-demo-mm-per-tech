package org.svenehrke.triptychdemo.feature.nonfood;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.stream.Collectors;

@ApplicationScoped
public class NonFoodDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("nonfood-deliveries")
    @Blocking
    public void receive(RawNonFoodDelivery message) {
        rejectUnprocessable(message);
        // Mapping: RawNonFoodDelivery -> NonFoodDelivery:
        switch (NonFoodDelivery.parse(message.productName(), message.quantity())) {
            case ParsedNonFoodDelivery.Invalid invalid: {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("NonFoodDeliveryReceiver: NON_FOOD_DELIVERY_RECEIVED",
                    "INVALID: %s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                break;
            }
            case NonFoodDelivery nonFoodDelivery: {
                auditLog.log("NonFoodDeliveryReceiver: NON_FOOD_DELIVERY_RECEIVED", nonFoodDelivery.productName() + " qty=" + nonFoodDelivery.quantity());
                inventoryAPI.updateNonFoodAmount(nonFoodDelivery);
                auditLog.log("NonFoodDeliveryReceiver: NON_FOOD_INVENTORY_UPDATED", nonFoodDelivery.productName() + " +" + nonFoodDelivery.quantity());
                break;
            }
        }
    }

    // Throwing nacks the message, which sends it to the dead-letter topic (failure-strategy=dead-letter-queue).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(RawNonFoodDelivery message) {
        if (message == null) {
            throw new IllegalArgumentException("null payload (tombstone)");
        }
    }
}
