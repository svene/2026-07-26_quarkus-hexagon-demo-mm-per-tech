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

    @Incoming("nonfood-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateNonFoodAmount(message.productName(), message.quantity());
    }
}
