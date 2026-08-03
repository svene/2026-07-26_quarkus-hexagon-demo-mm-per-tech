package com.example.hexademo.adapter.inbound.kafka.fruit;

import com.example.hexademo.core.application.APIs;
import com.example.hexademo.core.domain.FruitDelivery;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class FruitDeliveryReceiver {

    @Inject
    APIs.InventoryAPI inventoryAPI;

    @Incoming("fruit-deliveries")
    @Blocking
    public void receive(RawFruitDelivery message) {
        // Mapping: RawFruitDelivery -> FruitDelivery:
        var x = FruitDelivery.parse(message.productName(), message.quantity());
        // Validation:
        if (x.isEmpty()) return;
        // Processing:
        inventoryAPI.updateFruitAmount(x.get());
    }
}
