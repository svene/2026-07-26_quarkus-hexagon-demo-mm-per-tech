package com.example.hexdemo.adapter.inbound.kafka;

import com.example.hexdemo.core.domain.ProductType;
import com.example.hexdemo.core.port.in.InventoryAPI;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class BeveragesDeliveryReceiver {

    @Inject InventoryAPI inventoryAPI;

    @Incoming("beverages-deliveries")
    @Blocking
    public void receive(DeliveryMessage message) {
        inventoryAPI.updateAmount(message.productName(), ProductType.BEVERAGE, message.quantity());
    }
}
