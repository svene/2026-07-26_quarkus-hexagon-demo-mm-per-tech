package org.svenehrke.triptychdemo.feature.fruit;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogAPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryAPI;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class FruitDeliveryReceiver {

    @Inject
    InventoryAPI inventoryAPI;
    @Inject
    AuditLogAPI auditLog;

    @Incoming("fruit-deliveries")
    @Blocking
    public void receive(RawFruitDelivery message) {
        // Mapping: RawFruitDelivery -> FruitDelivery:
        var x = FruitDelivery.parse(message.productName(), message.quantity());
        // Validation:
        if (x.isEmpty()) return;
        // Processing:
        auditLog.log("FruitDeliveryReceiver: FRUIT_DELIVERY_RECEIVED", x.get().productName() + " qty=" + x.get().quantity());
        inventoryAPI.updateFruitAmount(x.get());
        auditLog.log("FruitDeliveryReceiver: FRUIT_INVENTORY_UPDATED", x.get().productName() + " +" + x.get().quantity());
    }
}
