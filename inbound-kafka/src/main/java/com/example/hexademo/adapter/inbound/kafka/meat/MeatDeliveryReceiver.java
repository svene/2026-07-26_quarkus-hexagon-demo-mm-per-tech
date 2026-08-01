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

    @Incoming("meat-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateMeatAmount(message.productName(), message.quantity());
    }
}
