package com.example.hexademo.adapter.inbound.kafka.nonfood;

import com.example.hexademo.core.application.APIs;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class NonFoodDeliveryReceiver {

    @Inject
    APIs.InventoryAPI inventoryAPI;
    @Inject
    APIs.AuditLogAPI auditLog;

    @Incoming("nonfood-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        auditLog.log("NonFoodDeliveryReceiver: NON_FOOD_DELIVERY_RECEIVED", message.productName() + " qty=" + message.quantity());
        inventoryAPI.updateNonFoodAmount(message.productName(), message.quantity());
        auditLog.log("NonFoodDeliveryReceiver: NON_FOOD_INVENTORY_UPDATED", message.productName() + " +" + message.quantity());
    }
}
