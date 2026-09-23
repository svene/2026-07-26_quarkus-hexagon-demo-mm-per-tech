package org.svenehrke.triptychdemo.feature.bakery;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.stream.Collectors;

@ApplicationScoped
public class BakeryDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("bakery-deliveries")
    @Blocking
    public void receive(RawBakeryDelivery message) {
        // Mapping: RawBakeryDelivery -> BakeryDelivery:
        switch (BakeryDelivery.parse(message.productName(), message.quantity())) {
            case ParsedBakeryDelivery.Invalid invalid: {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("BakeryDeliveryReceiver: BAKERY_DELIVERY_RECEIVED",
                    "INVALID: %s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                break;
            }
            case BakeryDelivery bakeryDelivery: {
                auditLog.log("BakeryDeliveryReceiver: BAKERY_DELIVERY_RECEIVED", bakeryDelivery.productName() + " qty=" + bakeryDelivery.quantity());
                inventoryAPI.updateBakeryAmount(bakeryDelivery);
                auditLog.log("BakeryDeliveryReceiver: BAKERY_INVENTORY_UPDATED", bakeryDelivery.productName() + " +" + bakeryDelivery.quantity());
                break;
            }
        }
    }
}
