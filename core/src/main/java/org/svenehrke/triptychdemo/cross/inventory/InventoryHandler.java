package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.feature.bakery.BakeryDelivery;
import org.svenehrke.triptychdemo.feature.beverage.BeverageDelivery;
import org.svenehrke.triptychdemo.feature.dairy.DairyDelivery;
import org.svenehrke.triptychdemo.feature.fruit.FruitDelivery;
import org.svenehrke.triptychdemo.feature.meat.MeatDelivery;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodDelivery;
import org.svenehrke.triptychdemo.feature.vegetable.VegetableDelivery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InventoryHandler {

    @Inject
    InventoryRepositorySPI inventoryRepository;

    public void updateFruitAmount(FruitDelivery fruitDelivery) {
        inventoryRepository.addAmount(fruitDelivery.productName(), ProductType.FRUIT, fruitDelivery.quantity());
    }

    public void updateVegetableAmount(VegetableDelivery vegetableDelivery) {
        inventoryRepository.addAmount(vegetableDelivery.productName(), ProductType.VEGETABLE, vegetableDelivery.quantity());
    }

    public void updateDairyAmount(DairyDelivery dairyDelivery) {
        inventoryRepository.addAmount(dairyDelivery.productName(), ProductType.DAIRY, dairyDelivery.quantity());
    }

    public void updateBeverageAmount(BeverageDelivery beverageDelivery) {
        inventoryRepository.addAmount(beverageDelivery.productName(), ProductType.BEVERAGE, beverageDelivery.quantity());
    }

    public void updateMeatAmount(MeatDelivery meatDelivery) {
        inventoryRepository.addAmount(meatDelivery.productName(), ProductType.MEAT, meatDelivery.quantity());
    }

    public void updateBakeryAmount(BakeryDelivery bakeryDelivery) {
        inventoryRepository.addAmount(bakeryDelivery.productName(), ProductType.BAKERY, bakeryDelivery.quantity());
    }

    public void updateNonFoodAmount(NonFoodDelivery nonFoodDelivery) {
        inventoryRepository.addAmount(nonFoodDelivery.productName(), ProductType.NON_FOOD, nonFoodDelivery.quantity());
    }
}
