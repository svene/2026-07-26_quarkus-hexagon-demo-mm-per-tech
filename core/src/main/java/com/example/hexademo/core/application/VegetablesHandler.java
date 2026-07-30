package com.example.hexademo.core.application;

import com.example.hexademo.core.port.in.VegetablesAPI;
import com.example.hexademo.core.port.out.AuditLogSPI;
import com.example.hexademo.core.port.out.VegetablesSupplierSPI;
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
