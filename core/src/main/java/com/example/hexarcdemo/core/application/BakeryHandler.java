package com.example.hexarcdemo.core.application;

import com.example.hexarcdemo.core.port.in.BakeryAPI;
import com.example.hexarcdemo.core.port.out.AuditLogSPI;
import com.example.hexarcdemo.core.port.out.BakerySupplierSPI;
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
