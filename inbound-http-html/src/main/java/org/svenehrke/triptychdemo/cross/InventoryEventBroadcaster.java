package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.occupancy.OccupancyChanged;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Hands every {@link InventoryEvent} and {@link OccupancyChanged} core fires to the open SSE streams of
 * {@link InventoryEventsReceiver} - one CDI observer per type, which the streams subscribe to as plain JDK
 * {@link Flow.Publisher}s.
 */
@ApplicationScoped
class InventoryEventBroadcaster {

    private final SubmissionPublisher<InventoryEvent> publisher = new SubmissionPublisher<>();
    private final SubmissionPublisher<OccupancyChanged> occupancyPublisher = new SubmissionPublisher<>();

    /** Never blocks: a stream that has fallen behind (full buffer) misses this event. */
    void onInventoryEvent(@ObservesAsync InventoryEvent event) {
        publisher.offer(event, (subscriber, dropped) -> false);
    }

    /** Never blocks, like {@link #onInventoryEvent}; a missed one is made good by the store's next report. */
    void onOccupancyChanged(@ObservesAsync OccupancyChanged event) {
        occupancyPublisher.offer(event, (subscriber, dropped) -> false);
    }

    Flow.Publisher<InventoryEvent> events() {
        return publisher;
    }

    Flow.Publisher<OccupancyChanged> occupancyEvents() {
        return occupancyPublisher;
    }

    @PreDestroy
    void close() {
        publisher.close();
        occupancyPublisher.close();
    }
}
