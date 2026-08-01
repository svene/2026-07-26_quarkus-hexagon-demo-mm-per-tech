package com.example.hexademo.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeveragesHandler implements APIs.BeveragesAPI {

    @Inject
    SPIs.BeverageSupplierSPI beverageSupplier;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_RECEIVED", productName + " qty=" + quantity);
        beverageSupplier.placeOrder(productName, quantity);
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
