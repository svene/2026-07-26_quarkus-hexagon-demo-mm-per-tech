package com.example.hexademo.adapter.inbound.kafka;

import com.example.hexademo.core.port.in.PurchaseAPI;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class CashpointReceiver {

    @Inject PurchaseAPI purchaseAPI;

    @Incoming("cashpoint-purchases")
    @Blocking
    public void receive(DeliveryMessage message) {
        purchaseAPI.purchase(message.productName(), message.quantity());
    }
}
