package com.example.hexademo.core.application;

import com.example.hexademo.core.port.in.NonFoodAPI;
import com.example.hexademo.core.port.out.AuditLogSPI;
import com.example.hexademo.core.port.out.NonFoodSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NonFoodHandler implements NonFoodAPI {

    @Inject NonFoodSupplierSPI nonFoodSupplier;
    @Inject AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_RECEIVED", productName + " qty=" + quantity);
        nonFoodSupplier.placeOrder(productName, quantity);
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
