package com.example.hexademo.adapter.inbound.kafka.meat;

import com.example.hexademo.core.application.APIs;
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
    public void receive(DeliveryMessage message) {
        auditLog.log("MeatDeliveryReceiver: MEAT_DELIVERY_RECEIVED", message.productName() + " qty=" + message.quantity());
        inventoryAPI.updateMeatAmount(message.productName(), message.quantity());
        auditLog.log("MeatDeliveryReceiver: MEAT_INVENTORY_UPDATED", message.productName() + " +" + message.quantity());
    }
}
