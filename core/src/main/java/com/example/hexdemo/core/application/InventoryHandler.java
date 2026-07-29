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
    public void updateAmount(String productName, ProductType type, int additionalAmount) {
        var updated = inventoryRepository.addAmount(productName, type, additionalAmount);
        eventPublisher.publishInventoryChanged(updated);
        auditLog.log("INVENTORY_UPDATED", productName + " +" + additionalAmount + " total=" + updated.availableAmount());
    }
}
