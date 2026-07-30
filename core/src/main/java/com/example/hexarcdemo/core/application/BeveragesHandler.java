package com.example.hexarcdemo.core.application;

import com.example.hexarcdemo.core.port.in.BeveragesAPI;
import com.example.hexarcdemo.core.port.out.AuditLogSPI;
import com.example.hexarcdemo.core.port.out.BeverageSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeveragesHandler implements BeveragesAPI {

    @Inject BeverageSupplierSPI beverageSupplier;
    @Inject AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_RECEIVED", productName + " qty=" + quantity);
        beverageSupplier.placeOrder(productName, quantity);
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
