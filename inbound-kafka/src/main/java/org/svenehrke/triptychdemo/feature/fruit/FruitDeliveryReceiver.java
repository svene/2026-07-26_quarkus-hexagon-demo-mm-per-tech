package org.svenehrke.triptychdemo.feature.fruit;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.stream.Collectors;

@ApplicationScoped
public class FruitDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("fruit-deliveries")
    @Blocking
    public void receive(RawFruitDelivery message) {
        rejectUnprocessable(message);
        // Mapping: RawFruitDelivery -> FruitDelivery:
        switch (FruitDelivery.parse(message.productName(), message.quantity())) {
            case ParsedFruitDelivery.Invalid invalid: {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("FruitDeliveryReceiver: FRUIT_DELIVERY_RECEIVED",
                    "INVALID: %s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                break;
            }
            case FruitDelivery fruitDelivery: {
                auditLog.log("FruitDeliveryReceiver: FRUIT_DELIVERY_RECEIVED", fruitDelivery.productName() + " qty=" + fruitDelivery.quantity());
                inventoryAPI.updateFruitAmount(fruitDelivery);
                auditLog.log("FruitDeliveryReceiver: FRUIT_INVENTORY_UPDATED", fruitDelivery.productName() + " +" + fruitDelivery.quantity());
                break;
            }
        }
    }

    // Throwing nacks the message, which sends it to the dead-letter topic (failure-strategy=dead-letter-queue).
    // Undeserializable messages never get here: SmallRye sends those to the DLQ directly.
    private static void rejectUnprocessable(RawFruitDelivery message) {
        if (message == null) {
            throw new IllegalArgumentException("null payload (tombstone)");
        }
    }
}
