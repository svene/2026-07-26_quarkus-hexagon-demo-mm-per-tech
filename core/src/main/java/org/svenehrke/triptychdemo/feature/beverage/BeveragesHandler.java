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
    public void order(String productName, int quantity) {
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_RECEIVED", productName + " qty=" + quantity);
        beverageSupplier.placeOrder(productName, quantity);
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
