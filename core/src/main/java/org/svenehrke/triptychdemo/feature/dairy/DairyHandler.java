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
    public void order(String productName, int quantity) {
        auditLog.log("DairyHandler: DAIRY_ORDER_RECEIVED", productName + " qty=" + quantity);
        dairySupplier.placeOrder(productName, quantity);
        auditLog.log("DairyHandler: DAIRY_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
