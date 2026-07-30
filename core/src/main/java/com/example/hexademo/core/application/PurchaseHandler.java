package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.PurchaseItem;
import com.example.hexademo.core.port.in.PurchaseAPI;
import com.example.hexademo.core.port.out.AuditLogSPI;
import com.example.hexademo.core.port.out.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class PurchaseHandler implements PurchaseAPI {

    @Inject InventoryRepositorySPI inventoryRepository;
    @Inject AuditLogSPI auditLog;

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
