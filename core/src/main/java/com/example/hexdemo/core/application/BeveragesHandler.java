package com.example.hexdemo.core.application;

import com.example.hexdemo.core.port.in.BeveragesAPI;
import com.example.hexdemo.core.port.out.AuditLogSPI;
import com.example.hexdemo.core.port.out.BeverageSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BeveragesHandler implements BeveragesAPI {

    @Inject BeverageSupplierSPI beverageSupplier;
    @Inject AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        beverageSupplier.placeOrder(productName, quantity);
        auditLog.log("ORDER_BEVERAGES", productName + " qty=" + quantity);
    }
}
