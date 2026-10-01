package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
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
    @Inject
    AuditLogSPI auditLog;
    @Inject
    InventoryChangesHandler inventoryChanges;

    public void updateFruitAmount(FruitDelivery fruitDelivery) {
        inventoryRepository.addAmount(fruitDelivery.productName(), ProductType.FRUIT, fruitDelivery.quantity());
        inventoryChanges.publishChange();
        auditLog.log("InventoryHandler: FRUIT_INVENTORY_UPDATED", fruitDelivery.productName() + " +" + fruitDelivery.quantity());
    }

    public void updateVegetableAmount(VegetableDelivery vegetableDelivery) {
        inventoryRepository.addAmount(vegetableDelivery.productName(), ProductType.VEGETABLE, vegetableDelivery.quantity());
        inventoryChanges.publishChange();
        auditLog.log("InventoryHandler: VEGETABLE_INVENTORY_UPDATED", vegetableDelivery.productName() + " +" + vegetableDelivery.quantity());
    }

    public void updateDairyAmount(DairyDelivery dairyDelivery) {
        inventoryRepository.addAmount(dairyDelivery.productName(), ProductType.DAIRY, dairyDelivery.quantity());
        inventoryChanges.publishChange();
        auditLog.log("InventoryHandler: DAIRY_INVENTORY_UPDATED", dairyDelivery.productName() + " +" + dairyDelivery.quantity());
    }

    public void updateBeverageAmount(BeverageDelivery beverageDelivery) {
        inventoryRepository.addAmount(beverageDelivery.productName(), ProductType.BEVERAGE, beverageDelivery.quantity());
        inventoryChanges.publishChange();
        auditLog.log("InventoryHandler: BEVERAGE_INVENTORY_UPDATED", beverageDelivery.productName() + " +" + beverageDelivery.quantity());
    }

    public void updateMeatAmount(MeatDelivery meatDelivery) {
        inventoryRepository.addAmount(meatDelivery.productName(), ProductType.MEAT, meatDelivery.quantity());
        inventoryChanges.publishChange();
        auditLog.log("InventoryHandler: MEAT_INVENTORY_UPDATED", meatDelivery.productName() + " +" + meatDelivery.quantity());
    }

    public void updateBakeryAmount(BakeryDelivery bakeryDelivery) {
        inventoryRepository.addAmount(bakeryDelivery.productName(), ProductType.BAKERY, bakeryDelivery.quantity());
        inventoryChanges.publishChange();
        auditLog.log("InventoryHandler: BAKERY_INVENTORY_UPDATED", bakeryDelivery.productName() + " +" + bakeryDelivery.quantity());
    }

    public void updateNonFoodAmount(NonFoodDelivery nonFoodDelivery) {
        inventoryRepository.addAmount(nonFoodDelivery.productName(), ProductType.NON_FOOD, nonFoodDelivery.quantity());
        inventoryChanges.publishChange();
        auditLog.log("InventoryHandler: NON_FOOD_INVENTORY_UPDATED", nonFoodDelivery.productName() + " +" + nonFoodDelivery.quantity());
    }
}
