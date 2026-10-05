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

    /**
     * SKIP: a period close that is still running (a slow database) is not overtaken by the next one. The first close
     * waits {@code inventory.first-period-close-delay} (one period): a close right at the start would end a period of
     * almost no length (learning a demand of 0), and the observers of {@code LevelsRecalculated} would call the
     * supplier stubs before the HTTP server listens. A property of its own: {@code delayed} doesn't accept {@code off}.
     */
    @Scheduled(every = "${inventory.demand-period}", delayed = "${inventory.first-period-close-delay}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void closePeriod() {
        reorderPolicyHandler.closePeriod();
    }
}
