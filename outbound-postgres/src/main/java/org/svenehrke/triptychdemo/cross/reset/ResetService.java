package org.svenehrke.triptychdemo.cross.reset;

import org.svenehrke.triptychdemo.cross.inventory.StockTable;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseTable;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderTable;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentRequestTable;
import org.svenehrke.triptychdemo.cross.replenishment.ShipmentTable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Bulk deletes; the identity columns keep counting, so new rows never reuse an id a message in flight still refers to. */
@ApplicationScoped
public class ResetService implements ResetRepositorySPI {

    @Inject
    StockTable stockTable;

    @Inject
    ReplenishmentRequestTable requests;

    @Inject
    ShipmentTable shipments;

    @Inject
    SupplierOrderTable orders;

    @Inject
    PurchaseTable purchases;

    @Override
    @Transactional
    public void deleteAll() {
        shipments.deleteAll();
        requests.deleteAll();
        orders.deleteAll();
        purchases.deleteAll();
        stockTable.deleteAll();
    }
}
