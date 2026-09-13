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
    public void order(String productName, int quantity) {
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_RECEIVED", productName + " qty=" + quantity);
        nonFoodSupplier.placeOrder(productName, quantity);
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
