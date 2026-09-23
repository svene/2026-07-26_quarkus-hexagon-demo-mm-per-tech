package org.svenehrke.triptychdemo.feature.nonfood;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NonFoodHandler implements NonFoodAPI {

    @Inject
    NonFoodSupplierSPI nonFoodSupplier;
    @Inject
    AuditLogSPI auditLog;

    @Override
    public void order(NonFoodOrder nonFoodOrder) {
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_RECEIVED", nonFoodOrder.productName() + " qty=" + nonFoodOrder.quantity());
        nonFoodSupplier.placeOrder(nonFoodOrder);
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_PLACED", nonFoodOrder.productName() + " qty=" + nonFoodOrder.quantity());
    }
}
