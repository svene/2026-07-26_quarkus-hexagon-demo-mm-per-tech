package com.example.hexademo.external.inbound.kafka.cashpoint;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class CashpointStub {

    @Inject
    @RestClient
    ProductsApiClient productsApiClient;

    @Inject
    @Channel("cashpoint-purchases-out")
    Emitter<PurchaseRequest> emitter;

    @Scheduled(every = "10s", delayed = "30s")
    void simulatePurchase() {
        List<ProductInfo> available;
        try {
            available = productsApiClient.listProducts().stream()
                .filter(p -> p.availableAmount() > 0)
                .toList();
        } catch (Exception e) {
            return;
        }
        if (available.isEmpty()) return;

        var rnd = ThreadLocalRandom.current();
        var shuffled = new ArrayList<>(available);
        Collections.shuffle(shuffled);
        int count = Math.min(rnd.nextInt(2, 5), shuffled.size());
        var items = shuffled.subList(0, count).stream()
            .map(p -> new PurchaseRequestItem(p.name(), rnd.nextInt(1, Math.min(4, p.availableAmount() + 1))))
            .toList();
        emitter.send(new PurchaseRequest(items));
    }
}
