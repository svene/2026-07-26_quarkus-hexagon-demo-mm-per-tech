package org.svenehrke.triptychdemo.feature.beverage;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeveragesHandler implements BeveragesAPI {

    @Inject
    BeverageSupplierSPI beverageSupplier;
    @Inject
    AuditLogSPI auditLog;

    @Override
    public void order(BeverageOrder beverageOrder) {
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_RECEIVED", beverageOrder.productName() + " qty=" + beverageOrder.quantity());
        beverageSupplier.placeOrder(beverageOrder);
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_PLACED", beverageOrder.productName() + " qty=" + beverageOrder.quantity());
    }
}
