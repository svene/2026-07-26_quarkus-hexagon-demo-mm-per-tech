package org.svenehrke.triptychdemo.cross.inventory;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Tells inbound adapters that the inventory changed, so e.g. the shop page can refresh itself right away
 * instead of polling. Not an SPI: the notifications flow from core to inbound adapters, which reach core only
 * through Handlers. Plain JDK {@link Flow}, so core needs no reactive library.
 * <p>
 * In-process only: with several app instances, a browser connected to one would miss changes made on another.
 */
@ApplicationScoped
public class InventoryChangesHandler {

    private final SubmissionPublisher<InventoryChanged> publisher = new SubmissionPublisher<>();

    /**
     * Call after the change is committed, so a subscriber re-reading the inventory sees it. Never blocks the
     * caller: a subscriber that has fallen behind (full buffer) misses this event.
     */
    public void publishChange() {
        publisher.offer(new InventoryChanged(), (subscriber, event) -> false);
    }

    public Flow.Publisher<InventoryChanged> changes() {
        return publisher;
    }

    @PreDestroy
    void close() {
        publisher.close();
    }

    /** Carries no data: subscribers re-read whatever they show. */
    public record InventoryChanged() {}
}
