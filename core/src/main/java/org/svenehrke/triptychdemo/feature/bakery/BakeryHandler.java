package org.svenehrke.triptychdemo.feature.bakery;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BakeryHandler implements BakeryAPI {

    @Inject
    BakerySupplierSPI bakerySupplier;
    @Inject
    AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("BakeryHandler: BAKERY_ORDER_RECEIVED", productName + " qty=" + quantity);
        bakerySupplier.placeOrder(productName, quantity);
        auditLog.log("BakeryHandler: BAKERY_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
