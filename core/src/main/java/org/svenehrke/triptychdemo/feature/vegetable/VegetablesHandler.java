package org.svenehrke.triptychdemo.feature.vegetable;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VegetablesHandler implements VegetablesAPI {

    @Inject
    VegetablesSupplierSPI vegetablesSupplier;
    @Inject
    AuditLogSPI auditLog;

    @Override
    public void order(VegetableOrder vegetableOrder) {
        auditLog.log("VegetablesHandler: VEGETABLES_ORDER_RECEIVED", vegetableOrder.productName() + " qty=" + vegetableOrder.quantity());
        vegetablesSupplier.placeOrder(vegetableOrder);
        auditLog.log("VegetablesHandler: VEGETABLES_ORDER_PLACED", vegetableOrder.productName() + " qty=" + vegetableOrder.quantity());
    }
}
