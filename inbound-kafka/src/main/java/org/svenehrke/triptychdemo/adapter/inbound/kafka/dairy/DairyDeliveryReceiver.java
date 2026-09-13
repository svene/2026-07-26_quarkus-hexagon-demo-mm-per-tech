package org.svenehrke.triptychdemo.adapter.inbound.kafka.dairy;

import org.svenehrke.triptychdemo.core.application.APIs;
import org.svenehrke.triptychdemo.core.domain.DairyDelivery;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class DairyDeliveryReceiver {

    @Inject
    APIs.InventoryAPI inventoryAPI;
    @Inject
    APIs.AuditLogAPI auditLog;

    @Incoming("dairy-deliveries")
    @Blocking
    public void receive(RawDairyDelivery message) {
        // Mapping: RawDairyDelivery -> DairyDelivery:
        var x = DairyDelivery.parse(message.productName(), message.quantity());
        // Validation:
        if (x.isEmpty()) return;
        // Processing:
        auditLog.log("DairyDeliveryReceiver: DAIRY_DELIVERY_RECEIVED", x.get().productName() + " qty=" + x.get().quantity());
        inventoryAPI.updateDairyAmount(x.get());
        auditLog.log("DairyDeliveryReceiver: DAIRY_INVENTORY_UPDATED", x.get().productName() + " +" + x.get().quantity());
    }
}
