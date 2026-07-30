package com.example.hexademo.core.application;

import com.example.hexademo.core.port.in.BakeryAPI;
import com.example.hexademo.core.port.out.AuditLogSPI;
import com.example.hexademo.core.port.out.BakerySupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BakeryHandler implements BakeryAPI {

    @Inject BakerySupplierSPI bakerySupplier;
    @Inject AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("BakeryHandler: BAKERY_ORDER_RECEIVED", productName + " qty=" + quantity);
        bakerySupplier.placeOrder(productName, quantity);
        auditLog.log("BakeryHandler: BAKERY_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
