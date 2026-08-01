package com.example.hexademo.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BakeryHandler implements APIs.BakeryAPI {

    @Inject
    SPIs.BakerySupplierSPI bakerySupplier;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("BakeryHandler: BAKERY_ORDER_RECEIVED", productName + " qty=" + quantity);
        bakerySupplier.placeOrder(productName, quantity);
        auditLog.log("BakeryHandler: BAKERY_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
