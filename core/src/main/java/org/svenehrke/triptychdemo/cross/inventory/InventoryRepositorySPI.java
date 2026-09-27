package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;

import java.util.List;
import java.util.SortedMap;

public interface InventoryRepositorySPI {
	Product addAmount(String name, ProductType type, int delta);

	/**
	 * Deducts all quantities in one transaction. Safe against concurrent callers: rows are locked, so two
	 * of them can never both take the last item. See {@link OnShortage} for what happens on a shortage.
	 */
	StockDeduction deductAll(SortedMap<String, Integer> quantitiesByName, OnShortage onShortage);

	List<Product> findAll();
}
