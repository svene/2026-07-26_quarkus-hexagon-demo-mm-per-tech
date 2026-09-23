package org.svenehrke.triptychdemo.cross.purchase;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.stream.Collectors;

@ApplicationScoped
public class PurchaseHandler implements PurchaseAPI {

    @Inject
    InventoryRepositorySPI inventoryRepository;
    @Inject
    AuditLogSPI auditLog;

    @Override
    public void purchase(Purchase purchase) {
        var items = purchase.items();
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
