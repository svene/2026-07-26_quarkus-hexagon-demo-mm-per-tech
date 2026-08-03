package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.FruitDelivery;
import com.example.hexademo.core.domain.ProductType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InventoryHandler implements APIs.InventoryAPI {

    @Inject
    SPIs.InventoryRepositorySPI inventoryRepository;

    @Override
    public void updateFruitAmount(FruitDelivery fruitDelivery) {
        inventoryRepository.addAmount(fruitDelivery.productName(), ProductType.FRUIT, fruitDelivery.quantity());
    }

    @Override
    public void updateVegetableAmount(String productName, int quantity) {
        inventoryRepository.addAmount(productName, ProductType.VEGETABLE, quantity);
    }

    @Override
    public void updateDairyAmount(String productName, int quantity) {
        inventoryRepository.addAmount(productName, ProductType.DAIRY, quantity);
    }

    @Override
    public void updateBeverageAmount(String productName, int quantity) {
        inventoryRepository.addAmount(productName, ProductType.BEVERAGE, quantity);
    }

    @Override
    public void updateMeatAmount(String productName, int quantity) {
        inventoryRepository.addAmount(productName, ProductType.MEAT, quantity);
    }

    @Override
    public void updateBakeryAmount(String productName, int quantity) {
        inventoryRepository.addAmount(productName, ProductType.BAKERY, quantity);
    }

    @Override
    public void updateNonFoodAmount(String productName, int quantity) {
        inventoryRepository.addAmount(productName, ProductType.NON_FOOD, quantity);
    }
}
