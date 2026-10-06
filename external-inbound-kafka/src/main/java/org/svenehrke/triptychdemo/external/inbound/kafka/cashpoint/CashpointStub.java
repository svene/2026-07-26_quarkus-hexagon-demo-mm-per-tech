package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * The checkout systems of the physical stores: one {@link StoreSimulation} per store, one purchase per customer
 * who pays at a till, and every {@value #REPORT_EVERY_SECONDS} s each store's occupancy (door counters and tills).
 */
@ApplicationScoped
public class CashpointStub {

    private static final Duration LOG_EVERY = Duration.ofSeconds(10);
    static final int REPORT_EVERY_SECONDS = 5;
    private static final Duration REPORT_EVERY = Duration.ofSeconds(REPORT_EVERY_SECONDS);
    /** What a store's checkout system accepts; core has the same limit ({@code TillCount.MAX_TILLS}). */
    static final int MAX_TILLS = 8;

    @Inject
    CashpointStubConfig config;

    @Inject
    @RestClient
    ProductsApiClient productsApiClient;

    @Inject
    @Channel("cashpoint-purchases-out")
    Emitter<PurchaseRequest> emitter;

    @Inject
    @Channel("store-occupancy-out")
    Emitter<OccupancyMessage> occupancyEmitter;

    private List<StoreSimulation> stores;
    /** Till changes from {@link CashpointTillsStub} (another thread), applied by the next tick. */
    private final Map<String, Integer> requestedTills = new ConcurrentHashMap<>();
    private Instant lastTick;
    private Instant lastLog;
    private Instant lastReport;

    @PostConstruct
    void init() {
        var timing = new SimulationTiming(config.day(), config.tillTime());
        var dayStart = Instant.now();
        stores = config.stores().entrySet().stream()
            .map(e -> new StoreSimulation(e.getKey(), e.getValue().capacity(), e.getValue().tills(), timing,
                RandomGenerator.getDefault(), dayStart))
            .toList();
    }

    /** False if there is no such store; the change takes effect with the next tick. */
    boolean requestTills(String storeId, int tills) {
        if (!config.stores().containsKey(storeId)) return false;
        requestedTills.put(storeId, tills);
        return true;
    }

    @Scheduled(every = "${cashpoint-stub.tick}", delayed = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        var now = Instant.now();
        var elapsed = lastTick == null ? Duration.ZERO : Duration.between(lastTick, now);
        lastTick = now;
        boolean report = lastReport == null || !now.isBefore(lastReport.plus(REPORT_EVERY));
        for (var store : stores) {
            var tills = requestedTills.remove(store.storeId());
            if (tills != null) store.setTills(tills);
            int paid = store.tick(now, elapsed);
            if (paid > 0) checkout(store.storeId(), paid);
            // right after a till change, too: the page shows its effect at once
            if (report || tills != null) reportOccupancy(store.report(now));
        }
        if (report) lastReport = now;
        if (lastLog == null || !now.isBefore(lastLog.plus(LOG_EVERY))) {
            lastLog = now;
            stores.stream().map(StoreSimulation::snapshot).forEach(s -> Log.infof(
                "Store %s: %d customers inside, %d queuing for a till; last %ds: %d entered, %d paid, %d turned away",
                s.storeId(), s.occupancy(), s.queued(), LOG_EVERY.toSeconds(), s.entered(), s.paid(), s.turnedAway()));
        }
    }

    /** Keyed by store: on a compacted topic, the latest report per store would be kept. */
    private void reportOccupancy(StoreSimulation.Occupancy occupancy) {
        occupancyEmitter.send(Message.of(OccupancyMessage.of(occupancy))
            .addMetadata(OutgoingKafkaRecordMetadata.<String>builder().withKey(occupancy.storeId()).build()));
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
