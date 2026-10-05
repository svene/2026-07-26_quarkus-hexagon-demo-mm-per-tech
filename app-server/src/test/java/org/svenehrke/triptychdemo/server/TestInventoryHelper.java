package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.jdbc.Db;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestInventoryHelper {

    @Inject Db db;

    /** The stock of every location, every replenishment request, shipment and supplier order. */
    @Transactional
    public void resetInventory() {
        db.update("DELETE FROM stock");
        db.update("DELETE FROM replenishment_request");
        db.update("DELETE FROM shipment");
        db.update("DELETE FROM supplier_order");
    }
}
