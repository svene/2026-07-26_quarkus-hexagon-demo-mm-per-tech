package org.svenehrke.triptychdemo.cross.reset;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.InventoryReset;
import org.svenehrke.triptychdemo.cross.events.AsyncEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resets the demo (head office, dev): deletes the inventory data and clears the audit log. Since Flyway keeps the
 * data across restarts, this is the way back to an empty demo.
 * <p>
 * Messages still in flight are harmless: a late supplier delivery just adds its quantity to the DC, a late shipment
 * arrival finds no shipment and is ignored.
 */
@ApplicationScoped
public class ResetHandler {

    @Inject
    ResetRepositorySPI repository;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    AsyncEvents inventoryEvents;

    public void reset() {
        repository.deleteAll();
        auditLog.clear();
        auditLog.log("ResetHandler: INVENTORY_RESET", "stock, requests, shipments, supplier orders, purchases and audit log deleted");
        inventoryEvents.fire(new InventoryReset());
    }
}
