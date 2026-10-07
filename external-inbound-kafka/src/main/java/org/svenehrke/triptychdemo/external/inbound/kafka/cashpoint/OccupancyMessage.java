package org.svenehrke.triptychdemo.external.inbound.kafka.cashpoint;

import java.time.Instant;

/**
 * What a store's door counters and tills report on the {@code store-occupancy} topic (Kafka key: the store id);
 * {@code paid} / {@code turnedAway}: customers who paid at a till / were turned away at the full store in the last
 * demo day.
 */
public record OccupancyMessage(String storeId, Instant measuredAt, int inside, int capacity, int queuing, int tills,
                               int tillsBusy, int paid, int turnedAway) {

    static OccupancyMessage of(StoreSimulation.Occupancy o) {
        return new OccupancyMessage(o.storeId(), o.measuredAt(), o.inside(), o.capacity(), o.queuing(), o.tills(),
            o.tillsBusy(), o.paid(), o.turnedAway());
    }
}
