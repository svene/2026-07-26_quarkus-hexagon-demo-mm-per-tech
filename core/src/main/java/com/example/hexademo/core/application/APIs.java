package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.AuditLogEntry;
import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.PurchaseItem;

import java.util.List;

public interface APIs {
	interface AuditLogAPI {
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
		void updateFruitAmount(String productName, int quantity);
		void updateVegetableAmount(String productName, int quantity);
		void updateDairyAmount(String productName, int quantity);
		void updateBeverageAmount(String productName, int quantity);
		void updateMeatAmount(String productName, int quantity);
		void updateBakeryAmount(String productName, int quantity);
		void updateNonFoodAmount(String productName, int quantity);
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
