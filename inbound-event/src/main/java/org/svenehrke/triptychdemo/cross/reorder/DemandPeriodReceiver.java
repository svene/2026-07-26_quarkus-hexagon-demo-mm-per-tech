package org.svenehrke.triptychdemo.cross.reorder;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The end of a demand period (a demo "day", {@code inventory.demand-period}): learns the reorder levels. Off with
 * {@code inventory.demand-period=off} (tests, e2e); tests then close the period by calling the Handler directly.
 */
@ApplicationScoped
public class DemandPeriodReceiver {

    @Inject
    ReorderPolicyHandler reorderPolicyHandler;

    /** SKIP: a period close that is still running (a slow database) is not overtaken by the next one. */
    @Scheduled(every = "${inventory.demand-period}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void closePeriod() {
        reorderPolicyHandler.closePeriod();
    }
}
