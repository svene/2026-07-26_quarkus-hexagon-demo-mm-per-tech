package org.svenehrke.triptychdemo.cross.reset;

import org.svenehrke.triptychdemo.cross.inventory.StockEntity;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderEntity;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentRequestEntity;
import org.svenehrke.triptychdemo.cross.replenishment.ShipmentEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Bulk deletes; the sequences are left alone, so new rows never reuse an id a message in flight still refers to. */
@ApplicationScoped
public class ResetService implements ResetRepositorySPI {

    @Override
    @Transactional
    public void deleteAll() {
        ShipmentEntity.deleteAll();
        ReplenishmentRequestEntity.deleteAll();
        SupplierOrderEntity.deleteAll();
        StockEntity.deleteAll();
    }
}
