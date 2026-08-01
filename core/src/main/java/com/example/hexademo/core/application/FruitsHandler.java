package com.example.hexademo.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FruitsHandler implements APIs.FruitsAPI {

    @Inject
    SPIs.FruitSupplierSPI fruitSupplier;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("FruitsHandler: FRUITS_ORDER_RECEIVED", productName + " qty=" + quantity);
        fruitSupplier.placeOrder(productName, quantity);
        auditLog.log("FruitsHandler: FRUITS_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
