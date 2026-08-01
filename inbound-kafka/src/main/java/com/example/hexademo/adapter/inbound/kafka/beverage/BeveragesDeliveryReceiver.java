package com.example.hexademo.adapter.inbound.kafka.beverage;

import com.example.hexademo.core.application.APIs;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class BeveragesDeliveryReceiver {

    @Inject
    APIs.InventoryAPI inventoryAPI;

    @Incoming("beverages-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateBeverageAmount(message.productName(), message.quantity());
    }
}
