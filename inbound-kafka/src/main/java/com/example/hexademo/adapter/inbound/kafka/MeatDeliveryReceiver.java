package com.example.hexademo.adapter.inbound.kafka;

import com.example.hexademo.core.port.in.InventoryAPI;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class MeatDeliveryReceiver {

    @Inject InventoryAPI inventoryAPI;

    @Incoming("meat-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateMeatAmount(message.productName(), message.quantity());
    }
}
