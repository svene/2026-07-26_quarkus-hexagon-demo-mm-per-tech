package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.PurchaseItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class PurchaseHandler implements APIs.PurchaseAPI {

    @Inject
    SPIs.InventoryRepositorySPI inventoryRepository;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void purchase(List<PurchaseItem> items) {
        auditLog.log("PurchaseHandler: PURCHASE_RECEIVED",
            items.stream().map(i -> i.productName() + " qty=" + i.quantity()).collect(Collectors.joining(", ")));

        var deducted = new ArrayList<String>();
        for (var item : items) {
            var updated = inventoryRepository.deductAmount(item.productName(), item.quantity());
            if (updated != null) {
                deducted.add(item.productName() + " -" + item.quantity() + " total=" + updated.availableAmount());
            } else {
                auditLog.log("PurchaseHandler: PRODUCT_NOT_FOUND", item.productName());
            }
        }
        if (!deducted.isEmpty()) {
            auditLog.log("PurchaseHandler: INVENTORY_DEDUCTED", String.join(", ", deducted));
        }
    }
}
