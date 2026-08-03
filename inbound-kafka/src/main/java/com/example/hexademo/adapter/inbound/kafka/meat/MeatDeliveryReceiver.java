package com.example.hexademo.adapter.inbound.kafka.meat;

import com.example.hexademo.core.application.APIs;
import com.example.hexademo.core.domain.MeatDelivery;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class MeatDeliveryReceiver {

    @Inject
    APIs.InventoryAPI inventoryAPI;
    @Inject
    APIs.AuditLogAPI auditLog;

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
