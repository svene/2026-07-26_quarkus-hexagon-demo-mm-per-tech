package com.example.hexademo.adapter.inbound.kafka.dairy;

import com.example.hexademo.core.application.APIs;
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
    public void receive(DeliveryMessage message) {
        auditLog.log("DairyDeliveryReceiver: DAIRY_DELIVERY_RECEIVED", message.productName() + " qty=" + message.quantity());
        inventoryAPI.updateDairyAmount(message.productName(), message.quantity());
        auditLog.log("DairyDeliveryReceiver: DAIRY_INVENTORY_UPDATED", message.productName() + " +" + message.quantity());
    }
}
