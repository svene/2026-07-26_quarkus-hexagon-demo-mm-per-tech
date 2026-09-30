package org.svenehrke.triptychdemo.feature.dairy;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DairyHandler {

    @Inject
    DairySupplierSPI dairySupplier;
    @Inject
    AuditLogSPI auditLog;

    public void order(DairyOrder dairyOrder) {
        auditLog.log("DairyHandler: DAIRY_ORDER_PROCESSING", dairyOrder.productName() + " qty=" + dairyOrder.quantity());
        dairySupplier.placeOrder(dairyOrder);
        auditLog.log("DairyHandler: DAIRY_ORDER_PLACED", dairyOrder.productName() + " qty=" + dairyOrder.quantity());
    }
}
