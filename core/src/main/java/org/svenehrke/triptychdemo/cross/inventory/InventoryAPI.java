package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.feature.bakery.BakeryDelivery;
import org.svenehrke.triptychdemo.feature.beverage.BeverageDelivery;
import org.svenehrke.triptychdemo.feature.dairy.DairyDelivery;
import org.svenehrke.triptychdemo.feature.fruit.FruitDelivery;
import org.svenehrke.triptychdemo.feature.meat.MeatDelivery;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodDelivery;
import org.svenehrke.triptychdemo.feature.vegetable.VegetableDelivery;

public interface InventoryAPI {
	void updateFruitAmount(FruitDelivery fruitDelivery);
	void updateVegetableAmount(VegetableDelivery vegetableDelivery);
	void updateDairyAmount(DairyDelivery dairyDelivery);
	void updateBeverageAmount(BeverageDelivery beverageDelivery);
	void updateMeatAmount(MeatDelivery meatDelivery);
	void updateBakeryAmount(BakeryDelivery bakeryDelivery);
	void updateNonFoodAmount(NonFoodDelivery nonFoodDelivery);
}
