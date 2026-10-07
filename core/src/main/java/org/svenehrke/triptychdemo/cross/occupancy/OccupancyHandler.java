package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.events.AsyncEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * The stores' occupancy as their checkout systems report it. Shown on {@code /locations}; the tills are opened and
 * closed by it ({@link AutoTillsHandler}).
 */
@ApplicationScoped
public class OccupancyHandler {

    @Inject
    OccupancyRepositorySPI repository;
    @Inject
    AsyncEvents occupancyEvents;

    /**
     * Idempotent: a report older than (or as old as) the stored one - redelivered, or overtaken by a newer one - is
     * ignored. Not audit-logged: every store reports every few seconds.
     */
    public void record(StoreOccupancy occupancy) {
        if (repository.saveIfNewer(occupancy)) {
            occupancyEvents.fire(new OccupancyChanged(occupancy.store()));
        }
    }

    public List<StoreOccupancy> current() {
        return repository.findAll();
    }
}
