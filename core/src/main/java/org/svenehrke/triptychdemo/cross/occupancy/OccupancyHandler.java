package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.events.AsyncEvents;
import org.svenehrke.triptychdemo.cross.location.Store;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The stores' occupancy as their checkout systems report it. Shown on {@code /locations}, the latest report and the
 * history of the last {@link #WINDOW}; the tills are opened and closed by it ({@link AutoTillsHandler}).
 */
@ApplicationScoped
public class OccupancyHandler {

    /** How far back {@link #history(Store)} reaches: 10 demo days (1 demo day = 1 real minute), several rush hours. */
    public static final Duration WINDOW = Duration.ofMinutes(10);
    /** How long a report stays in the history: longer than the window, so the window is always full. */
    static final Duration RETENTION = Duration.ofMinutes(30);

    @Inject
    OccupancyRepositorySPI repository;
    @Inject
    AsyncEvents occupancyEvents;

    /**
     * Idempotent: a report older than (or as old as) the stored one - redelivered, or overtaken by a newer one - is
     * ignored. A newer one is also added to the history. Not audit-logged: every store reports every few seconds.
     */
    public void record(StoreOccupancy occupancy) {
        if (repository.saveIfNewer(occupancy)) {
            repository.appendToHistory(occupancy, occupancy.measuredAt().minus(RETENTION));
            occupancyEvents.fire(new OccupancyChanged(occupancy.store()));
        }
    }

    public List<StoreOccupancy> current() {
        return repository.findAll();
    }

    /** The store's reports of the last {@link #WINDOW}, oldest first. */
    public List<StoreOccupancy> history(Store store) {
        return repository.history(store, Instant.now().minus(WINDOW));
    }
}
