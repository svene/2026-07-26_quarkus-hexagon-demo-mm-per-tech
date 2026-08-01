package com.example.hexademo.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NonFoodHandler implements APIs.NonFoodAPI {

    @Inject
    SPIs.NonFoodSupplierSPI nonFoodSupplier;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_RECEIVED", productName + " qty=" + quantity);
        nonFoodSupplier.placeOrder(productName, quantity);
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
