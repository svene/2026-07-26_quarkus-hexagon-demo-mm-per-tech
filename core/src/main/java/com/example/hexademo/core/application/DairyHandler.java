package com.example.hexademo.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DairyHandler implements APIs.DairyAPI {

    @Inject
    SPIs.DairySupplierSPI dairySupplier;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("DairyHandler: DAIRY_ORDER_RECEIVED", productName + " qty=" + quantity);
        dairySupplier.placeOrder(productName, quantity);
        auditLog.log("DairyHandler: DAIRY_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
