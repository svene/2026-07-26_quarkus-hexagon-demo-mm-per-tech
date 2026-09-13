package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;

import java.util.List;

public interface InventoryRepositorySPI {
	Product addAmount(String name, ProductType type, int delta);
	Product deductAmount(String name, int delta);
	List<Product> findAll();
}
