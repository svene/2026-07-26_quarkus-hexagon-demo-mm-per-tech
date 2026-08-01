package com.example.hexademo.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VegetablesHandler implements APIs.VegetablesAPI {

    @Inject
    SPIs.VegetablesSupplierSPI vegetablesSupplier;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("VegetablesHandler: VEGETABLES_ORDER_RECEIVED", productName + " qty=" + quantity);
        vegetablesSupplier.placeOrder(productName, quantity);
        auditLog.log("VegetablesHandler: VEGETABLES_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
