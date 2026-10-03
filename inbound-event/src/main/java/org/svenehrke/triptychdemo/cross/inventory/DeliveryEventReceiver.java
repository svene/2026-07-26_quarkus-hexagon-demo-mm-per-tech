package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentHandler;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

/** A delivery reached the DC: serve the requests that were waiting for it. */
@ApplicationScoped
public class DeliveryEventReceiver {

    @Inject
    ReplenishmentHandler replenishmentHandler;
    @Inject
    AuditLogHandler auditLog;

    /**
     * Nobody waits for an async observer, so a failure would vanish silently - it is audit-logged instead. The
     * requests then stay pending until the next delivery of the product, or until head office fulfils them.
     */
    void onDeliveredToDc(@ObservesAsync DeliveredToDc event) {
        auditLog.log("DeliveryEventReceiver: DELIVERED_TO_DC_RECEIVED", event.productName());
        try {
            replenishmentHandler.fulfilPending(event.productName());
        } catch (RuntimeException e) {
            Log.errorf(e, "Serving the pending requests of %s failed", event.productName());
            auditLog.log("DeliveryEventReceiver: FULFIL_PENDING_FAILED", event.productName() + ": " + e);
        }
    }
}
