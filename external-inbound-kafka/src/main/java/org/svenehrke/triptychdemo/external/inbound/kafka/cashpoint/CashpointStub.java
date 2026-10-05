package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * The checkout systems of the physical stores: one {@link StoreSimulation} per store, and one purchase per customer
 * who pays at a till.
 */
@ApplicationScoped
public class CashpointStub {

    private static final Duration LOG_EVERY = Duration.ofSeconds(10);

    @Inject
    CashpointStubConfig config;

    @Inject
    @RestClient
    ProductsApiClient productsApiClient;

    @Inject
    @Channel("cashpoint-purchases-out")
    Emitter<PurchaseRequest> emitter;

    private List<StoreSimulation> stores;
    private Instant lastTick;
    private Instant lastLog;

    @PostConstruct
    void init() {
        var timing = new SimulationTiming(config.day(), config.tillTime());
        var dayStart = Instant.now();
        stores = config.stores().entrySet().stream()
            .map(e -> new StoreSimulation(e.getKey(), e.getValue().tills(), timing,
                RandomGenerator.getDefault(), dayStart))
            .toList();
    }

    @Scheduled(every = "${cashpoint-stub.tick}", delayed = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        var now = Instant.now();
        var elapsed = lastTick == null ? Duration.ZERO : Duration.between(lastTick, now);
        lastTick = now;
        for (var store : stores) {
            int paid = store.tick(now, elapsed);
            if (paid > 0) checkout(store.storeId(), paid);
        }
        if (lastLog == null || !now.isBefore(lastLog.plus(LOG_EVERY))) {
            lastLog = now;
            stores.stream().map(StoreSimulation::snapshot).forEach(s -> Log.infof(
                "Store %s: %d customers inside, %d queuing for a till; last %ds: %d entered, %d paid",
                s.storeId(), s.occupancy(), s.queued(), LOG_EVERY.toSeconds(), s.entered(), s.paid()));
        }
    }

    /** One purchase per paying customer, from what the store has in stock; nothing in stock → no purchase. */
    private void checkout(String storeId, int customers) {
        List<ProductInfo> available;
        try {
            available = productsApiClient.listProducts(storeId).stream()
                .filter(p -> p.availableAmount() > 0)
                .toList();
        } catch (Exception e) {
            return;
        }
        if (available.isEmpty()) return;

        var rnd = ThreadLocalRandom.current();
        for (int c = 0; c < customers; c++) {
            var shuffled = new ArrayList<>(available);
            Collections.shuffle(shuffled);
            int count = Math.min(rnd.nextInt(2, 5), shuffled.size());
            var items = shuffled.subList(0, count).stream()
                .map(p -> new PurchaseRequestItem(p.name(), rnd.nextInt(1, Math.min(4, p.availableAmount() + 1))))
                .toList();
            emitter.send(new PurchaseRequest(storeId, items));
        }
    }
}
