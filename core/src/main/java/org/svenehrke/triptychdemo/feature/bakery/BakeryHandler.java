package org.svenehrke.triptychdemo.feature.bakery;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BakeryHandler {

    @Inject
    BakerySupplierSPI bakerySupplier;
    @Inject
    AuditLogSPI auditLog;

    public void order(BakeryOrder bakeryOrder) {
        auditLog.log("BakeryHandler: BAKERY_ORDER_PROCESSING", bakeryOrder.productName() + " qty=" + bakeryOrder.quantity());
        bakerySupplier.placeOrder(bakeryOrder);
        auditLog.log("BakeryHandler: BAKERY_ORDER_PLACED", bakeryOrder.productName() + " qty=" + bakeryOrder.quantity());
    }
}
