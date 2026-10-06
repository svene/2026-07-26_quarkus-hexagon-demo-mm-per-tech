package org.svenehrke.triptychdemo.cross.events;

import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.occupancy.OccupancyChanged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.inject.Inject;

import java.util.concurrent.Executor;

/**
 * Fires core's events to their async observers ({@code Event.fireAsync}) on the {@link EventExecutor}, instead of
 * CDI's default executor (Quarkus' platform worker pool): the observers call Handlers that block (JDBC, supplier
 * calls), and in this app they run on a virtual thread (see docs/architecture/virtual-threads-in-this-project.md).
 * The observers of one event are still notified one after another, in one task.
 */
@ApplicationScoped
public class AsyncEvents {

    @Inject
    Event<InventoryEvent> inventoryEvents;
    @Inject
    Event<OccupancyChanged> occupancyEvents;
    @Inject
    @EventExecutor
    Executor executor;

    public void fire(InventoryEvent event) {
        inventoryEvents.fireAsync(event, NotificationOptions.ofExecutor(executor));
    }

    public void fire(OccupancyChanged event) {
        occupancyEvents.fireAsync(event, NotificationOptions.ofExecutor(executor));
    }
}
