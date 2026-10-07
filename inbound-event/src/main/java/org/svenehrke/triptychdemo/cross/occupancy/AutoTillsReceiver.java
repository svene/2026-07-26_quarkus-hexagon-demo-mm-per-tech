package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

/**
 * Automatic tills: whenever a store reports a newer occupancy, its tills are re-judged. Off with
 * {@code inventory.auto-tills.enabled=false} (tests, e2e), so the tills then stay as configured in the checkout
 * systems. Looked up per event, so a test can switch it on without its own Quarkus instance.
 */
@ApplicationScoped
public class AutoTillsReceiver {

    @ConfigProperty(name = "inventory.auto-tills.enabled")
    Supplier<Boolean> enabled;
    @Inject
    AutoTillsHandler autoTillsHandler;
    @Inject
    AuditLogHandler auditLog;

    /** Nobody waits for an async observer, so a failure is audit-logged; the next report judges again. */
    void onOccupancyChanged(@ObservesAsync OccupancyChanged event) {
        if (!enabled.get()) return;
        try {
            autoTillsHandler.adjust(event.store());
        } catch (RuntimeException e) {
            Log.errorf(e, "Automatic tills of %s failed", event.store().id());
            auditLog.log("AutoTillsReceiver: AUTO_TILLS_FAILED", event.store().id() + ": " + e);
        }
    }
}
