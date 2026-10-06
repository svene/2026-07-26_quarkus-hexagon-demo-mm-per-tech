package org.svenehrke.triptychdemo.cross.occupancy;

import java.time.Instant;

/**
 * A store's occupancy as its checkout system reports it (Kafka key: {@code storeId}). Boxed numbers, so a missing
 * field is noticed instead of read as 0.
 */
public record OccupancyMessage(String storeId, Instant measuredAt, Integer inside, Integer capacity, Integer queuing,
                               Integer tills, Integer tillsBusy, Integer turnedAway) {}
