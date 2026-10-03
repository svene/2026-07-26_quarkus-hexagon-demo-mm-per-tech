package org.svenehrke.triptychdemo.cross.reorder;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.inventory.LevelsRecalculated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/** Learning the reorder levels: at the end of each demand period (a demo "day"). */
@ApplicationScoped
public class ReorderPolicyHandler {

    @Inject
    InventoryRepositorySPI inventoryRepository;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    Event<InventoryEvent> inventoryEvents;

    /**
     * Folds the period's demand into every stock row's estimate and recalculates its levels (see
     * {@link InventoryRepositorySPI#closePeriod()}), then fires {@link LevelsRecalculated}, so every location re-checks
     * its stock against the new levels.
     */
    public void closePeriod() {
        int rows = inventoryRepository.closePeriod();
        auditLog.log("ReorderPolicyHandler: PERIOD_CLOSED", rows + " rows");
        inventoryEvents.fireAsync(new LevelsRecalculated());
    }
}
