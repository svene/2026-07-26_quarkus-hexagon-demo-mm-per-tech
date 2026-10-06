package org.svenehrke.triptychdemo.cross.events;

import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Core's async events ({@link AsyncEvents}) are delivered on virtual threads: the observers in this module call
 * Handlers that block (JDBC, supplier calls), and a virtual thread doesn't hold a platform thread while it waits.
 * One virtual thread per event; its observers still run one after another on it.
 */
@ApplicationScoped
public class EventExecutorProducer {

    @Produces
    @EventExecutor
    Executor eventExecutor(@VirtualThreads ExecutorService virtualThreads) {
        return virtualThreads;
    }
}
