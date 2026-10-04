package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrder;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderRepositorySPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryDelivery;
import org.svenehrke.triptychdemo.feature.beverage.BeverageDelivery;
import org.svenehrke.triptychdemo.feature.dairy.DairyDelivery;
import org.svenehrke.triptychdemo.feature.fruit.FruitDelivery;
import org.svenehrke.triptychdemo.feature.meat.MeatDelivery;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodDelivery;
import org.svenehrke.triptychdemo.feature.vegetable.VegetableDelivery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Supplier deliveries: they all go to the DC, and close the open supplier orders of their product oldest first.
 * Serving the requests waiting for a delivery is not done here but by whoever observes {@link DeliveredToDc} (see there
 * why).
 */
@ApplicationScoped
public class InventoryHandler {

    @Inject
    SupplierOrderRepositorySPI supplierOrders;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    Event<InventoryEvent> inventoryEvents;

    public void updateFruitAmount(FruitDelivery fruitDelivery) {
        receive(fruitDelivery.productName(), ProductType.FRUIT, fruitDelivery.quantity());
        auditLog.log("InventoryHandler: FRUIT_INVENTORY_UPDATED", "dc: " + fruitDelivery.productName() + " +" + fruitDelivery.quantity());
        inventoryEvents.fireAsync(new DeliveredToDc(fruitDelivery.productName()));
    }

    public void updateVegetableAmount(VegetableDelivery vegetableDelivery) {
        receive(vegetableDelivery.productName(), ProductType.VEGETABLE, vegetableDelivery.quantity());
        auditLog.log("InventoryHandler: VEGETABLE_INVENTORY_UPDATED", "dc: " + vegetableDelivery.productName() + " +" + vegetableDelivery.quantity());
        inventoryEvents.fireAsync(new DeliveredToDc(vegetableDelivery.productName()));
    }

    public void updateDairyAmount(DairyDelivery dairyDelivery) {
        receive(dairyDelivery.productName(), ProductType.DAIRY, dairyDelivery.quantity());
        auditLog.log("InventoryHandler: DAIRY_INVENTORY_UPDATED", "dc: " + dairyDelivery.productName() + " +" + dairyDelivery.quantity());
        inventoryEvents.fireAsync(new DeliveredToDc(dairyDelivery.productName()));
    }

    public void updateBeverageAmount(BeverageDelivery beverageDelivery) {
        receive(beverageDelivery.productName(), ProductType.BEVERAGE, beverageDelivery.quantity());
        auditLog.log("InventoryHandler: BEVERAGE_INVENTORY_UPDATED", "dc: " + beverageDelivery.productName() + " +" + beverageDelivery.quantity());
        inventoryEvents.fireAsync(new DeliveredToDc(beverageDelivery.productName()));
    }

    public void updateMeatAmount(MeatDelivery meatDelivery) {
        receive(meatDelivery.productName(), ProductType.MEAT, meatDelivery.quantity());
        auditLog.log("InventoryHandler: MEAT_INVENTORY_UPDATED", "dc: " + meatDelivery.productName() + " +" + meatDelivery.quantity());
        inventoryEvents.fireAsync(new DeliveredToDc(meatDelivery.productName()));
    }

    public void updateBakeryAmount(BakeryDelivery bakeryDelivery) {
        receive(bakeryDelivery.productName(), ProductType.BAKERY, bakeryDelivery.quantity());
        auditLog.log("InventoryHandler: BAKERY_INVENTORY_UPDATED", "dc: " + bakeryDelivery.productName() + " +" + bakeryDelivery.quantity());
        inventoryEvents.fireAsync(new DeliveredToDc(bakeryDelivery.productName()));
    }

    public void updateNonFoodAmount(NonFoodDelivery nonFoodDelivery) {
        receive(nonFoodDelivery.productName(), ProductType.NON_FOOD, nonFoodDelivery.quantity());
        auditLog.log("InventoryHandler: NON_FOOD_INVENTORY_UPDATED", "dc: " + nonFoodDelivery.productName() + " +" + nonFoodDelivery.quantity());
        inventoryEvents.fireAsync(new DeliveredToDc(nonFoodDelivery.productName()));
    }

    private void receive(String productName, ProductType type, int quantity) {
        supplierOrders.receiveDelivery(productName, type, quantity).stream().map(SupplierOrder::describe)
            .forEach(order -> auditLog.log("InventoryHandler: SUPPLIER_ORDER_DELIVERED", order));
    }
}
