package com.example.hexademo.adapter.inbound.kafka.bakery;

import com.example.hexademo.core.application.APIs;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class BakeryDeliveryReceiver {

    @Inject
    APIs.InventoryAPI inventoryAPI;
    @Inject
    APIs.AuditLogAPI auditLog;

    @Incoming("bakery-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        auditLog.log("BakeryDeliveryReceiver: BAKERY_DELIVERY_RECEIVED", message.productName() + " qty=" + message.quantity());
        inventoryAPI.updateBakeryAmount(message.productName(), message.quantity());
        auditLog.log("BakeryDeliveryReceiver: BAKERY_INVENTORY_UPDATED", message.productName() + " +" + message.quantity());
    }
}
