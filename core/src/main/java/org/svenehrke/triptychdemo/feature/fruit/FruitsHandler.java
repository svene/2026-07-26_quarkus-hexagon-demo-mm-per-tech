package org.svenehrke.triptychdemo.feature.fruit;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FruitsHandler implements FruitsAPI {

    @Inject
    FruitSupplierSPI fruitSupplier;
    @Inject
    AuditLogSPI auditLog;

    @Override
    public void order(FruitOrder fruitOrder) {
        auditLog.log("FruitsHandler: FRUITS_ORDER_RECEIVED", fruitOrder.productName() + " qty=" + fruitOrder.quantity());
        fruitSupplier.placeOrder(fruitOrder);
        auditLog.log("FruitsHandler: FRUITS_ORDER_PLACED", fruitOrder.productName() + " qty=" + fruitOrder.quantity());
    }
}
