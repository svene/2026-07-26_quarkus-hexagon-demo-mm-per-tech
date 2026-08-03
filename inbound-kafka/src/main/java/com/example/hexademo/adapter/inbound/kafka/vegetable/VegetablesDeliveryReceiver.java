package com.example.hexademo.adapter.inbound.kafka.vegetable;

import com.example.hexademo.core.application.APIs;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class VegetablesDeliveryReceiver {

    @Inject
    APIs.InventoryAPI inventoryAPI;
    @Inject
    APIs.AuditLogAPI auditLog;

    @Incoming("vegetables-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        auditLog.log("VegetablesDeliveryReceiver: VEGETABLE_DELIVERY_RECEIVED", message.productName() + " qty=" + message.quantity());
        inventoryAPI.updateVegetableAmount(message.productName(), message.quantity());
        auditLog.log("VegetablesDeliveryReceiver: VEGETABLE_INVENTORY_UPDATED", message.productName() + " +" + message.quantity());
    }
}
