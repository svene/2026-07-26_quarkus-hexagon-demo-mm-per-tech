package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.events.AsyncEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * The stores' occupancy as their checkout systems report it, and opening/closing their tills. The app shows the
 * occupancy but doesn't act on it.
 */
@ApplicationScoped
public class OccupancyHandler {

    @Inject
    OccupancyRepositorySPI repository;
    @Inject
    CheckoutSystemSPI checkoutSystem;
    @Inject
    AuditLogSPI auditLog;
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

    /** False if the checkout system refused the change or could not be reached (audit-logged). */
    public boolean setTills(TillCount tillCount) {
        var details = tillCount.store().id() + ": tills=" + tillCount.tills();
        try {
            checkoutSystem.setTills(tillCount);
        } catch (RuntimeException e) {
            auditLog.log("OccupancyHandler: TILLS_CHANGE_FAILED", details + ": " + e.getMessage());
            return false;
        }
        auditLog.log("OccupancyHandler: TILLS_CHANGED", details);
        return true;
    }
}
