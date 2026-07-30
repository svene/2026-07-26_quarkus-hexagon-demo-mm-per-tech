package com.example.hexademo.external.inbound.kafka.cashpoint;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class CashpointStub {

    private static final List<String> PRODUCTS = List.of(
        "Apple", "Banana", "Cola", "Milk", "Bread", "Steak", "Shampoo"
    );

    @Inject
    @Channel("cashpoint-purchases-out")
    Emitter<PurchaseRequest> emitter;

    @Scheduled(every = "2s", delayed = "30s")
    void simulatePurchase() {
        var rnd = ThreadLocalRandom.current();
        var productName = PRODUCTS.get(rnd.nextInt(PRODUCTS.size()));
        emitter.send(new PurchaseRequest(productName, rnd.nextInt(1, 4)));
    }
}
