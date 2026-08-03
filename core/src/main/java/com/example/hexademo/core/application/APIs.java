package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.AuditLogEntry;
import com.example.hexademo.core.domain.BakeryDelivery;
import com.example.hexademo.core.domain.BeverageDelivery;
import com.example.hexademo.core.domain.DairyDelivery;
import com.example.hexademo.core.domain.FruitDelivery;
import com.example.hexademo.core.domain.MeatDelivery;
import com.example.hexademo.core.domain.NonFoodDelivery;
import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.PurchaseItem;
import com.example.hexademo.core.domain.VegetableDelivery;

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
