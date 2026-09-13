package org.svenehrke.triptychdemo.core.application;

import org.svenehrke.triptychdemo.core.domain.FruitDelivery;
import org.svenehrke.triptychdemo.core.domain.BakeryDelivery;
import org.svenehrke.triptychdemo.core.domain.BeverageDelivery;
import org.svenehrke.triptychdemo.core.domain.DairyDelivery;
import org.svenehrke.triptychdemo.core.domain.MeatDelivery;
import org.svenehrke.triptychdemo.core.domain.NonFoodDelivery;
import org.svenehrke.triptychdemo.core.domain.ProductType;
import org.svenehrke.triptychdemo.core.domain.VegetableDelivery;
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
