package com.example.hexarcdemo.adapter.inbound.kafka;

import com.example.hexarcdemo.core.port.in.InventoryAPI;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class FruitDeliveryReceiver {

    @Inject InventoryAPI inventoryAPI;

    @Incoming("fruit-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateFruitAmount(message.productName(), message.quantity());
    }
}
