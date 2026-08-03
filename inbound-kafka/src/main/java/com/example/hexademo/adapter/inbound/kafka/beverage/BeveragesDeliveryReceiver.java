package com.example.hexademo.adapter.inbound.kafka.beverage;

import com.example.hexademo.core.application.APIs;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class BeveragesDeliveryReceiver {

    @Inject
    APIs.InventoryAPI inventoryAPI;
    @Inject
    APIs.AuditLogAPI auditLog;

    @Incoming("beverages-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        auditLog.log("BeveragesDeliveryReceiver: BEVERAGE_DELIVERY_RECEIVED", message.productName() + " qty=" + message.quantity());
        inventoryAPI.updateBeverageAmount(message.productName(), message.quantity());
        auditLog.log("BeveragesDeliveryReceiver: BEVERAGE_INVENTORY_UPDATED", message.productName() + " +" + message.quantity());
    }
}
