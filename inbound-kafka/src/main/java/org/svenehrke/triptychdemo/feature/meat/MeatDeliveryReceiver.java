package org.svenehrke.triptychdemo.feature.meat;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

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
        var x = MeatDelivery.parse(message.productName(), message.quantity());
        // Validation:
        if (x.isEmpty()) return;
        // Processing:
        auditLog.log("MeatDeliveryReceiver: MEAT_DELIVERY_RECEIVED", x.get().productName() + " qty=" + x.get().quantity());
        inventoryAPI.updateMeatAmount(x.get());
        auditLog.log("MeatDeliveryReceiver: MEAT_INVENTORY_UPDATED", x.get().productName() + " +" + x.get().quantity());
    }
}
