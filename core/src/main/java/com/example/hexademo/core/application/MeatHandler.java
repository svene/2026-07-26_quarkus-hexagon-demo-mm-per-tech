package com.example.hexademo.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MeatHandler implements APIs.MeatAPI {

    @Inject
    SPIs.MeatSupplierSPI meatSupplier;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("MeatHandler: MEAT_ORDER_RECEIVED", productName + " qty=" + quantity);
        meatSupplier.placeOrder(productName, quantity);
        auditLog.log("MeatHandler: MEAT_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
