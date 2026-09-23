package org.svenehrke.triptychdemo.feature.dairy;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.stream.Collectors;

@ApplicationScoped
public class DairyDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("dairy-deliveries")
    @Blocking
    public void receive(RawDairyDelivery message) {
        // Mapping: RawDairyDelivery -> DairyDelivery:
        switch (DairyDelivery.parse(message.productName(), message.quantity())) {
            case ParsedDairyDelivery.Invalid invalid: {
                String errors = invalid.violations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
                auditLog.log("DairyDeliveryReceiver: DAIRY_DELIVERY_RECEIVED",
                    "INVALID: %s, %d: %s".formatted(message.productName(), message.quantity(), errors));
                break;
            }
            case DairyDelivery dairyDelivery: {
                auditLog.log("DairyDeliveryReceiver: DAIRY_DELIVERY_RECEIVED", dairyDelivery.productName() + " qty=" + dairyDelivery.quantity());
                inventoryAPI.updateDairyAmount(dairyDelivery);
                auditLog.log("DairyDeliveryReceiver: DAIRY_INVENTORY_UPDATED", dairyDelivery.productName() + " +" + dairyDelivery.quantity());
                break;
            }
        }
    }
}
