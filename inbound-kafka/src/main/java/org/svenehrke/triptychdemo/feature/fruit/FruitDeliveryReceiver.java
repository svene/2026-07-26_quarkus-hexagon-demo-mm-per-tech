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
        switch (FruitDelivery.parse(message.productName(), message.quantity())) {
            case ParsedFruitDelivery.Invalid invalid: {
                auditLog.log("FruitDeliveryReceiver: FRUIT_DELIVERY_RECEIVED", "INVALID: " + String.join(",", invalid.errors()));
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
}
