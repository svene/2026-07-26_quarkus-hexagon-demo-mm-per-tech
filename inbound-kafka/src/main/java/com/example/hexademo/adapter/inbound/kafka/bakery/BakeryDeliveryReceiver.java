package com.example.hexademo.adapter.inbound.kafka.bakery;

import com.example.hexademo.core.port.in.InventoryAPI;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class BakeryDeliveryReceiver {

    @Inject InventoryAPI inventoryAPI;

    @Incoming("bakery-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateBakeryAmount(message.productName(), message.quantity());
    }
}
