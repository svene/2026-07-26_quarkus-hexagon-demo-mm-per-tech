package com.example.hexademo.adapter.inbound.kafka.vegetable;

import com.example.hexademo.core.application.APIs;
import com.example.hexademo.core.domain.VegetableDelivery;
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
    public void receive(RawVegetableDelivery message) {
        // Mapping: RawVegetableDelivery -> VegetableDelivery:
        var x = VegetableDelivery.parse(message.productName(), message.quantity());
        // Validation:
        if (x.isEmpty()) return;
        // Processing:
        auditLog.log("VegetablesDeliveryReceiver: VEGETABLE_DELIVERY_RECEIVED", x.get().productName() + " qty=" + x.get().quantity());
        inventoryAPI.updateVegetableAmount(x.get());
        auditLog.log("VegetablesDeliveryReceiver: VEGETABLE_INVENTORY_UPDATED", x.get().productName() + " +" + x.get().quantity());
    }
}
