package com.example.hexademo.core.application;

import com.example.hexademo.core.port.in.PurchaseAPI;
import com.example.hexademo.core.port.out.AuditLogSPI;
import com.example.hexademo.core.port.out.InventoryEventPublisherSPI;
import com.example.hexademo.core.port.out.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PurchaseHandler implements PurchaseAPI {

    @Inject InventoryRepositorySPI inventoryRepository;
    @Inject InventoryEventPublisherSPI eventPublisher;
    @Inject AuditLogSPI auditLog;

    @Override
    public void purchase(String productName, int quantity) {
        auditLog.log("PurchaseHandler: PURCHASE_RECEIVED", productName + " qty=" + quantity);
        var updated = inventoryRepository.deductAmount(productName, quantity);
        if (updated != null) {
            eventPublisher.publishInventoryChanged(updated);
            auditLog.log("PurchaseHandler: INVENTORY_DEDUCTED", productName + " -" + quantity + " total=" + updated.availableAmount());
        } else {
            auditLog.log("PurchaseHandler: PRODUCT_NOT_FOUND", productName);
        }
    }
}
