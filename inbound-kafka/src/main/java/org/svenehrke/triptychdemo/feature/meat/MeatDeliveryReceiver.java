package org.svenehrke.triptychdemo.feature.meat;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.stream.Collectors;

@ApplicationScoped
public class MeatDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("meat-deliveries")
    @Blocking
    public void receive(RawMeatDelivery message) {
        // Mapping: RawMeatDelivery -> MeatDelivery:
        switch (MeatDelivery.parse(message.productName(), message.quantity())) {
            case ParsedMeatDelivery.Invalid invalid: {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("MeatDeliveryReceiver: MEAT_DELIVERY_RECEIVED",
                    "INVALID: %s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                break;
            }
            case MeatDelivery meatDelivery: {
                auditLog.log("MeatDeliveryReceiver: MEAT_DELIVERY_RECEIVED", meatDelivery.productName() + " qty=" + meatDelivery.quantity());
                inventoryAPI.updateMeatAmount(meatDelivery);
                auditLog.log("MeatDeliveryReceiver: MEAT_INVENTORY_UPDATED", meatDelivery.productName() + " +" + meatDelivery.quantity());
                break;
            }
        }
    }
}
