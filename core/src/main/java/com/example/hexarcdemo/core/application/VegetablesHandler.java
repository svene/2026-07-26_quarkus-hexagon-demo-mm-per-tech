package com.example.hexarcdemo.core.application;

import com.example.hexarcdemo.core.port.in.VegetablesAPI;
import com.example.hexarcdemo.core.port.out.AuditLogSPI;
import com.example.hexarcdemo.core.port.out.VegetablesSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VegetablesHandler implements VegetablesAPI {

    @Inject VegetablesSupplierSPI vegetablesSupplier;
    @Inject AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("VegetablesHandler: VEGETABLES_ORDER_RECEIVED", productName + " qty=" + quantity);
        vegetablesSupplier.placeOrder(productName, quantity);
        auditLog.log("VegetablesHandler: VEGETABLES_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
