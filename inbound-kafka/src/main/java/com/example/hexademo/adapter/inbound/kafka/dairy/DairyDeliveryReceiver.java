package com.example.hexademo.adapter.inbound.kafka.dairy;

import com.example.hexademo.core.api.InventoryAPI;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class DairyDeliveryReceiver {

    @Inject InventoryAPI inventoryAPI;

    @Incoming("dairy-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateDairyAmount(message.productName(), message.quantity());
    }
}
