package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

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

    /** The stores of the supermarket chain; each tick, the cashpoint of a random one sells from its stock. */
    private static final List<String> STORE_IDS = List.of("zurich", "bern", "basel");

    @Inject
    @RestClient
    ProductsApiClient productsApiClient;

    @Inject
    @Channel("cashpoint-purchases-out")
    Emitter<PurchaseRequest> emitter;

    @Scheduled(every = "10s", delayed = "30s")
    void simulatePurchase() {
        var rnd = ThreadLocalRandom.current();
        var storeId = STORE_IDS.get(rnd.nextInt(STORE_IDS.size()));
        List<ProductInfo> available;
        try {
            available = productsApiClient.listProducts(storeId).stream()
                .filter(p -> p.availableAmount() > 0)
                .toList();
        } catch (Exception e) {
            return;
        }
        if (available.isEmpty()) return;

        var shuffled = new ArrayList<>(available);
        Collections.shuffle(shuffled);
        int count = Math.min(rnd.nextInt(2, 5), shuffled.size());
        var items = shuffled.subList(0, count).stream()
            .map(p -> new PurchaseRequestItem(p.name(), rnd.nextInt(1, Math.min(4, p.availableAmount() + 1))))
            .toList();
        emitter.send(new PurchaseRequest(storeId, items));
    }
}
