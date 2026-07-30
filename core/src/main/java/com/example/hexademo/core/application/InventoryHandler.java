package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.ProductType;
import com.example.hexademo.core.port.in.InventoryAPI;
import com.example.hexademo.core.port.out.AuditLogSPI;
import com.example.hexademo.core.port.out.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InventoryHandler implements InventoryAPI {

    @Inject InventoryRepositorySPI inventoryRepository;
    @Inject AuditLogSPI auditLog;

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
