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
    public void order(MeatOrder meatOrder) {
        auditLog.log("MeatHandler: MEAT_ORDER_RECEIVED", meatOrder.productName() + " qty=" + meatOrder.quantity());
        meatSupplier.placeOrder(meatOrder);
        auditLog.log("MeatHandler: MEAT_ORDER_PLACED", meatOrder.productName() + " qty=" + meatOrder.quantity());
    }
}
