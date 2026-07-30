package com.example.hexdemo.core.application;

import com.example.hexdemo.core.domain.ProductType;
import com.example.hexdemo.core.port.in.InventoryAPI;
import com.example.hexdemo.core.port.out.AuditLogSPI;
import com.example.hexdemo.core.port.out.InventoryEventPublisherSPI;
import com.example.hexdemo.core.port.out.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InventoryHandler implements InventoryAPI {

    @Inject InventoryRepositorySPI inventoryRepository;
    @Inject InventoryEventPublisherSPI eventPublisher;
    @Inject AuditLogSPI auditLog;

    @Override
    public void updateFruitAmount(String productName, int quantity) {
        auditLog.log("InventoryHandler: FRUIT_DELIVERY_RECEIVED", productName + " qty=" + quantity);
        var updated = inventoryRepository.addAmount(productName, ProductType.FRUIT, quantity);
        eventPublisher.publishInventoryChanged(updated);
        auditLog.log("InventoryHandler: FRUIT_INVENTORY_UPDATED", productName + " +" + quantity + " total=" + updated.availableAmount());
    }

    @Override
    public void updateBeverageAmount(String productName, int quantity) {
        auditLog.log("InventoryHandler: BEVERAGE_DELIVERY_RECEIVED", productName + " qty=" + quantity);
        var updated = inventoryRepository.addAmount(productName, ProductType.BEVERAGE, quantity);
        eventPublisher.publishInventoryChanged(updated);
        auditLog.log("InventoryHandler: BEVERAGE_INVENTORY_UPDATED", productName + " +" + quantity + " total=" + updated.availableAmount());
    }
}
