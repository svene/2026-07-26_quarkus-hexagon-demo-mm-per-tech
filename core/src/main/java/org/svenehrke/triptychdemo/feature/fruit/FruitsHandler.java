package org.svenehrke.triptychdemo.feature.fruit;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FruitsHandler {

    @Inject
    FruitSupplierSPI fruitSupplier;
    @Inject
    AuditLogSPI auditLog;

    public void order(FruitOrder fruitOrder) {
        auditLog.log("FruitsHandler: FRUITS_ORDER_PROCESSING", fruitOrder.productName() + " qty=" + fruitOrder.quantity());
        fruitSupplier.placeOrder(fruitOrder);
        auditLog.log("FruitsHandler: FRUITS_ORDER_PLACED", fruitOrder.productName() + " qty=" + fruitOrder.quantity());
    }
}
