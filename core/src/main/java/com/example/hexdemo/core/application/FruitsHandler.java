package com.example.hexdemo.core.application;

import com.example.hexdemo.core.port.in.FruitsAPI;
import com.example.hexdemo.core.port.out.AuditLogSPI;
import com.example.hexdemo.core.port.out.FruitSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FruitsHandler implements FruitsAPI {

    @Inject FruitSupplierSPI fruitSupplier;
    @Inject AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        fruitSupplier.placeOrder(productName, quantity);
        auditLog.log("ORDER_FRUITS", productName + " qty=" + quantity);
    }
}
