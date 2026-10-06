package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.SupplierOrdersChanged;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Singleton;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Records the thread core's async events are delivered on (see {@link AsyncEventsTest}). {@code @Singleton}: no client
 * proxy, so the test reads the real field.
 */
@Singleton
public class TestEventThreadRecorder {

    final Queue<Thread> threads = new ConcurrentLinkedQueue<>();

    void onSupplierOrdersChanged(@ObservesAsync SupplierOrdersChanged event) {
        threads.add(Thread.currentThread());
    }
}
