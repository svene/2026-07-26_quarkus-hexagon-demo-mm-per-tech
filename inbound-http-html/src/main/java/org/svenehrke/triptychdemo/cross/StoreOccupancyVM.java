package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.location.Store;
import org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancy;
import org.svenehrke.triptychdemo.cross.occupancy.TillCount;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * One store's occupancy line on {@code /locations}. {@code report} is null until the store's checkout system has
 * reported; {@code full}: inside reached the capacity, new customers are turned away; {@code turnedAway}: in the
 * last demo day; {@code stale}: the latest report is older than {@link #STALE_AFTER} (the checkout system stopped
 * reporting). {@code measuredAt} is local time with seconds precision ({@code HH:mm:ss}).
 */
public record StoreOccupancyVM(String storeId, ReportVM report, int maxTills) {

    static final Duration STALE_AFTER = Duration.ofSeconds(30);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public record ReportVM(int inside, int capacity, boolean full, int queuing, int tills, int tillsBusy,
                           int turnedAway, String measuredAt, boolean stale) {}

    static StoreOccupancyVM of(Store store, Optional<StoreOccupancy> occupancy, Instant now) {
        return new StoreOccupancyVM(store.id(), occupancy.map(o -> new ReportVM(o.inside(), o.capacity(),
            o.inside() >= o.capacity(), o.queuing(), o.tills(), o.tillsBusy(), o.turnedAway(), TIME.format(o.measuredAt()),
            o.measuredAt().isBefore(now.minus(STALE_AFTER)))).orElse(null),
            TillCount.MAX_TILLS);
    }
}
