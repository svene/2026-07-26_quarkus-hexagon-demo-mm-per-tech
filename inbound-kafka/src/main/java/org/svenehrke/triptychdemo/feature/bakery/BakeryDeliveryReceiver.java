package org.svenehrke.triptychdemo.feature.bakery;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

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
        var x = BakeryDelivery.parse(message.productName(), message.quantity());
        // Validation:
        if (x.isEmpty()) return;
        // Processing:
        auditLog.log("BakeryDeliveryReceiver: BAKERY_DELIVERY_RECEIVED", x.get().productName() + " qty=" + x.get().quantity());
        inventoryAPI.updateBakeryAmount(x.get());
        auditLog.log("BakeryDeliveryReceiver: BAKERY_INVENTORY_UPDATED", x.get().productName() + " +" + x.get().quantity());
    }
}
