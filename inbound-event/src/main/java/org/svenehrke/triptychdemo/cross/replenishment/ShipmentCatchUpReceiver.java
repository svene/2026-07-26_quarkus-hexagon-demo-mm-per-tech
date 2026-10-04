package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.inventory.LevelsRecalculated;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

/**
 * At every period close, sends the shipments that are in transit for longer than
 * {@code inventory.shipment-redispatch-after} to the carrier again - the catch-up for a dispatch or an arrival that got
 * lost. Without it such a shipment would stay in transit forever, and since it counts towards its location's inventory
 * position, the location would never request that quantity again.
 */
@ApplicationScoped
public class ShipmentCatchUpReceiver {

    @ConfigProperty(name = "inventory.shipment-redispatch-after")
    Duration redispatchAfter;
    @Inject
    ReplenishmentHandler replenishmentHandler;
    @Inject
    AuditLogHandler auditLog;

    /** Nobody waits for an async observer, so a failure is audit-logged; the next period close tries again. */
    void onLevelsRecalculated(@ObservesAsync LevelsRecalculated event) {
        try {
            replenishmentHandler.redispatchOverdue(redispatchAfter);
        } catch (RuntimeException e) {
            Log.error("Redispatching overdue shipments failed", e);
            auditLog.log("ShipmentCatchUpReceiver: REDISPATCH_FAILED", e.toString());
        }
    }
}
