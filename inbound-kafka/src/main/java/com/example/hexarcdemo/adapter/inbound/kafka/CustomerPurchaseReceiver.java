package com.example.hexarcdemo.adapter.inbound.kafka;

import com.example.hexarcdemo.core.port.in.PurchaseAPI;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class CustomerPurchaseReceiver {

    @Inject PurchaseAPI purchaseAPI;

    @Incoming("customer-purchases")
    @Blocking
    public void receive(DeliveryMessage message) {
        purchaseAPI.purchase(message.productName(), message.quantity());
    }
}
