package com.example.hexademo.adapter.inbound.kafka.beverage;

import com.example.hexademo.core.application.APIs;
import com.example.hexademo.core.domain.BeverageDelivery;
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
    public void receive(RawBeverageDelivery message) {
        // Mapping: RawBeverageDelivery -> BeverageDelivery:
        var x = BeverageDelivery.parse(message.productName(), message.quantity());
        // Validation:
        if (x.isEmpty()) return;
        // Processing:
        auditLog.log("BeveragesDeliveryReceiver: BEVERAGE_DELIVERY_RECEIVED", x.get().productName() + " qty=" + x.get().quantity());
        inventoryAPI.updateBeverageAmount(x.get());
        auditLog.log("BeveragesDeliveryReceiver: BEVERAGE_INVENTORY_UPDATED", x.get().productName() + " +" + x.get().quantity());
    }
}
