package org.svenehrke.triptychdemo.core.application;

import org.svenehrke.triptychdemo.core.domain.AuditLogEntry;
import org.svenehrke.triptychdemo.core.domain.BakeryDelivery;
import org.svenehrke.triptychdemo.core.domain.BeverageDelivery;
import org.svenehrke.triptychdemo.core.domain.DairyDelivery;
import org.svenehrke.triptychdemo.core.domain.FruitDelivery;
import org.svenehrke.triptychdemo.core.domain.MeatDelivery;
import org.svenehrke.triptychdemo.core.domain.NonFoodDelivery;
import org.svenehrke.triptychdemo.core.domain.Product;
import org.svenehrke.triptychdemo.core.domain.PurchaseItem;
import org.svenehrke.triptychdemo.core.domain.VegetableDelivery;

import java.util.List;

public interface APIs {
	interface AuditLogAPI {
		void log(String event, String details);
		List<AuditLogEntry> recent(int limit);
	}

	interface BakeryAPI {
		void order(String productName, int quantity);
	}

	interface BeveragesAPI {
		void order(String productName, int quantity);
	}

	interface DairyAPI {
		void order(String productName, int quantity);
	}

	interface FruitsAPI {
		void order(String productName, int quantity);
	}

	interface InventoryAPI {
		void updateFruitAmount(FruitDelivery fruitDelivery);
		void updateVegetableAmount(VegetableDelivery vegetableDelivery);
		void updateDairyAmount(DairyDelivery dairyDelivery);
		void updateBeverageAmount(BeverageDelivery beverageDelivery);
		void updateMeatAmount(MeatDelivery meatDelivery);
		void updateBakeryAmount(BakeryDelivery bakeryDelivery);
		void updateNonFoodAmount(NonFoodDelivery nonFoodDelivery);
	}

	interface MeatAPI {
		void order(String productName, int quantity);
	}

	interface NonFoodAPI {
		void order(String productName, int quantity);
	}

	interface ProductsAPI {
		List<Product> listAll();
	}

	interface PurchaseAPI {
		void purchase(List<PurchaseItem> items);
	}

	interface VegetablesAPI {
		void order(String productName, int quantity);
	}
}
