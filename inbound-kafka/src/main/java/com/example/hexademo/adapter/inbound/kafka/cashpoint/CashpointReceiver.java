package com.example.hexademo.adapter.inbound.kafka.cashpoint;

import com.example.hexademo.core.application.APIs;
import com.example.hexademo.core.domain.PurchaseItem;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class CashpointReceiver {

    @Inject
    APIs.PurchaseAPI purchaseAPI;

    @Incoming("cashpoint-purchases")
    @Blocking
    public void receive(PurchaseMessage message) {
        var items = message.items().stream()
            .map(i -> new PurchaseItem(i.productName(), i.quantity()))
            .toList();
        purchaseAPI.purchase(items);
    }
}
