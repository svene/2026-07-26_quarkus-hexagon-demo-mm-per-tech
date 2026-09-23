package org.svenehrke.triptychdemo.feature.dairy;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DairyHandler implements DairyAPI {

    @Inject
    DairySupplierSPI dairySupplier;
    @Inject
    AuditLogSPI auditLog;

    @Override
    public void order(DairyOrder dairyOrder) {
        auditLog.log("DairyHandler: DAIRY_ORDER_RECEIVED", dairyOrder.productName() + " qty=" + dairyOrder.quantity());
        dairySupplier.placeOrder(dairyOrder);
        auditLog.log("DairyHandler: DAIRY_ORDER_PLACED", dairyOrder.productName() + " qty=" + dairyOrder.quantity());
    }
}
