package com.example.hexarcdemo.core.application;

import com.example.hexarcdemo.core.port.in.FruitsAPI;
import com.example.hexarcdemo.core.port.out.AuditLogSPI;
import com.example.hexarcdemo.core.port.out.FruitSupplierSPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FruitsHandler implements FruitsAPI {

    @Inject FruitSupplierSPI fruitSupplier;
    @Inject AuditLogSPI auditLog;

    @Override
    public void order(String productName, int quantity) {
        auditLog.log("FruitsHandler: FRUITS_ORDER_RECEIVED", productName + " qty=" + quantity);
        fruitSupplier.placeOrder(productName, quantity);
        auditLog.log("FruitsHandler: FRUITS_ORDER_PLACED", productName + " qty=" + quantity);
    }
}
