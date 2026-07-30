package com.example.hexarcdemo.core.application;

import com.example.hexarcdemo.core.port.in.DairyAPI;
import com.example.hexarcdemo.core.port.out.AuditLogSPI;
import com.example.hexarcdemo.core.port.out.DairySupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DairyHandler implements DairyAPI {

    @Inject DairySupplierSPI dairySupplier;
    @Inject AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("DairyHandler: DAIRY_ORDER_RECEIVED", productName + " qty=" + quantity);
        dairySupplier.placeOrder(productName, quantity);
        auditLog.log("DairyHandler: DAIRY_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
