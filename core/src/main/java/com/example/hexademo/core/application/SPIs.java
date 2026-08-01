package com.example.hexademo.core.application;

import com.example.hexademo.core.domain.AuditLogEntry;
import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.ProductType;

import java.util.List;

public interface SPIs {
	interface AuditLogSPI {
		void log(String event, String details);
		List<AuditLogEntry> findRecent(int limit);
	}

	interface BakerySupplierSPI {
		void placeOrder(String productName, int quantity);
	}

	interface BeverageSupplierSPI {
		void placeOrder(String productName, int quantity);
	}

	interface DairySupplierSPI {
		void placeOrder(String productName, int quantity);
	}

	interface FruitSupplierSPI {
		void placeOrder(String productName, int quantity);
	}

	interface InventoryRepositorySPI {
		Product addAmount(String name, ProductType type, int delta);
		Product deductAmount(String name, int delta);
		List<Product> findAll();
	}

	interface MeatSupplierSPI {
		void placeOrder(String productName, int quantity);
	}

	interface NonFoodSupplierSPI {
		void placeOrder(String productName, int quantity);
	}

	interface VegetablesSupplierSPI {
		void placeOrder(String productName, int quantity);
	}
}
