package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.FruitDelivery;
import com.example.hexademo.core.domain.BakeryDelivery;
import com.example.hexademo.core.domain.BeverageDelivery;
import com.example.hexademo.core.domain.DairyDelivery;
import com.example.hexademo.core.domain.MeatDelivery;
import com.example.hexademo.core.domain.NonFoodDelivery;
import com.example.hexademo.core.domain.ProductType;
import com.example.hexademo.core.domain.VegetableDelivery;
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
    public void updateVegetableAmount(VegetableDelivery vegetableDelivery) {
        inventoryRepository.addAmount(vegetableDelivery.productName(), ProductType.VEGETABLE, vegetableDelivery.quantity());
    }

    @Override
    public void updateDairyAmount(DairyDelivery dairyDelivery) {
        inventoryRepository.addAmount(dairyDelivery.productName(), ProductType.DAIRY, dairyDelivery.quantity());
    }

    @Override
    public void updateBeverageAmount(BeverageDelivery beverageDelivery) {
        inventoryRepository.addAmount(beverageDelivery.productName(), ProductType.BEVERAGE, beverageDelivery.quantity());
    }

    @Override
    public void updateMeatAmount(MeatDelivery meatDelivery) {
        inventoryRepository.addAmount(meatDelivery.productName(), ProductType.MEAT, meatDelivery.quantity());
    }

    @Override
    public void updateBakeryAmount(BakeryDelivery bakeryDelivery) {
        inventoryRepository.addAmount(bakeryDelivery.productName(), ProductType.BAKERY, bakeryDelivery.quantity());
    }

    @Override
    public void updateNonFoodAmount(NonFoodDelivery nonFoodDelivery) {
        inventoryRepository.addAmount(nonFoodDelivery.productName(), ProductType.NON_FOOD, nonFoodDelivery.quantity());
    }
}
