package com.example.hexademo.adapter.inbound.kafka.nonfood;

import com.example.hexademo.core.application.APIs;
import com.example.hexademo.core.domain.NonFoodDelivery;
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
    public void receive(RawNonFoodDelivery message) {
        // Mapping: RawNonFoodDelivery -> NonFoodDelivery:
        var x = NonFoodDelivery.parse(message.productName(), message.quantity());
        // Validation:
        if (x.isEmpty()) return;
        // Processing:
        auditLog.log("NonFoodDeliveryReceiver: NON_FOOD_DELIVERY_RECEIVED", x.get().productName() + " qty=" + x.get().quantity());
        inventoryAPI.updateNonFoodAmount(x.get());
        auditLog.log("NonFoodDeliveryReceiver: NON_FOOD_INVENTORY_UPDATED", x.get().productName() + " +" + x.get().quantity());
    }
}
