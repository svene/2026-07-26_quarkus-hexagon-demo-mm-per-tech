package com.example.hexademo.external.inbound.kafka.cashpoint;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class CashpointStub {

    private final CopyOnWriteArrayList<String> inStockProducts = new CopyOnWriteArrayList<>();

    @Inject
    @Channel("cashpoint-purchases-out")
    Emitter<PurchaseRequest> emitter;

    @Incoming("inventory-events-consumer")
    public void onInventoryChanged(InventoryEvent event) {
        if (event.availableAmount() > 0) {
            inStockProducts.addIfAbsent(event.productName());
        } else {
            inStockProducts.remove(event.productName());
        }
    }

    @Scheduled(every = "2s", delayed = "30s")
    void simulatePurchase() {
        if (inStockProducts.isEmpty()) return;
        var rnd = ThreadLocalRandom.current();
        var productName = inStockProducts.get(rnd.nextInt(inStockProducts.size()));
        int qty = rnd.nextInt(1, 4);
        emitter.send(new PurchaseRequest(productName, qty));
    }
}
