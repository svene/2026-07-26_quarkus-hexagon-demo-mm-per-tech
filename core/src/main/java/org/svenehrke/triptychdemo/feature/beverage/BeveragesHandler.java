package org.svenehrke.triptychdemo.feature.beverage;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeveragesHandler {

    @Inject
    BeverageSupplierSPI beverageSupplier;
    @Inject
    AuditLogSPI auditLog;

    public void order(BeverageOrder beverageOrder) {
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_PROCESSING", beverageOrder.productName() + " qty=" + beverageOrder.quantity());
        beverageSupplier.placeOrder(beverageOrder);
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_PLACED", beverageOrder.productName() + " qty=" + beverageOrder.quantity());
    }
}
