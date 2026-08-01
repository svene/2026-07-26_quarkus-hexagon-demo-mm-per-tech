package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.ProductType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * NOTE: Do not merge the individual update methods!
 * It could be easily done.
 * BUT: the reason why they are separate is that the audit log proves that the the expected Receiver
 * triggered the call which we want to verify in the *FlowTests.
 * It would be better if the Receivers themselves write an audit-log. But that would mean an addition
 * API for AuditLogAPI would be needed which it would not nice because it is not for a business use-case, only for internal reasons.
 * Therefore this compromise here.
 */
@ApplicationScoped
public class InventoryHandler implements APIs.InventoryAPI {

    @Inject
    SPIs.InventoryRepositorySPI inventoryRepository;
    @Inject
    SPIs.AuditLogSPI auditLog;

    @Override
    public void updateFruitAmount(String productName, int quantity) {
        update(productName, ProductType.FRUIT, quantity, "FRUIT");
    }

    @Override
    public void updateVegetableAmount(String productName, int quantity) {
        update(productName, ProductType.VEGETABLE, quantity, "VEGETABLE");
    }

    @Override
    public void updateDairyAmount(String productName, int quantity) {
        update(productName, ProductType.DAIRY, quantity, "DAIRY");
    }

    @Override
    public void updateBeverageAmount(String productName, int quantity) {
        update(productName, ProductType.BEVERAGE, quantity, "BEVERAGE");
    }

    @Override
    public void updateMeatAmount(String productName, int quantity) {
        update(productName, ProductType.MEAT, quantity, "MEAT");
    }

    @Override
    public void updateBakeryAmount(String productName, int quantity) {
        update(productName, ProductType.BAKERY, quantity, "BAKERY");
    }

    @Override
    public void updateNonFoodAmount(String productName, int quantity) {
        update(productName, ProductType.NON_FOOD, quantity, "NON_FOOD");
    }

    private void update(String productName, ProductType type, int quantity, String typeLabel) {
        auditLog.log("InventoryHandler: " + typeLabel + "_DELIVERY_RECEIVED", productName + " qty=" + quantity);
        Product updated = inventoryRepository.addAmount(productName, type, quantity);
        auditLog.log("InventoryHandler: " + typeLabel + "_INVENTORY_UPDATED", productName + " +" + quantity + " total=" + updated.availableAmount());
    }
}
