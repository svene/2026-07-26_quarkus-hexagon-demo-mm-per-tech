package org.svenehrke.triptychdemo.feature.meat;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MeatHandler implements MeatAPI {

    @Inject
    MeatSupplierSPI meatSupplier;
    @Inject
    AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("MeatHandler: MEAT_ORDER_RECEIVED", productName + " qty=" + quantity);
        meatSupplier.placeOrder(productName, quantity);
        auditLog.log("MeatHandler: MEAT_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
