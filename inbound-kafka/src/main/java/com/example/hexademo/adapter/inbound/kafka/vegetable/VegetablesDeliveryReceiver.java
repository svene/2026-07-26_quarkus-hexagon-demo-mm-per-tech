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

    @Incoming("vegetables-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateVegetableAmount(message.productName(), message.quantity());
    }
}
