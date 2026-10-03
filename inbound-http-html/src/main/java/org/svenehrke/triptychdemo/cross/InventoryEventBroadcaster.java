package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Hands every {@link InventoryEvent} core fires to the open SSE streams of {@link InventoryEventsReceiver} - one
 * CDI observer for all of them, which the streams subscribe to as a plain JDK {@link Flow.Publisher}.
 */
@ApplicationScoped
class InventoryEventBroadcaster {

    private final SubmissionPublisher<InventoryEvent> publisher = new SubmissionPublisher<>();

    /** Never blocks: a stream that has fallen behind (full buffer) misses this event. */
    void onInventoryEvent(@ObservesAsync InventoryEvent event) {
        publisher.offer(event, (subscriber, dropped) -> false);
    }

    Flow.Publisher<InventoryEvent> events() {
        return publisher;
    }

    @PreDestroy
    void close() {
        publisher.close();
    }
}
