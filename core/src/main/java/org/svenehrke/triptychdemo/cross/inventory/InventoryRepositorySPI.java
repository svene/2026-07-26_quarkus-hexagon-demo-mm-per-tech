package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;

import java.util.List;
import java.util.SortedMap;

/** Stock per location; moving stock between locations is {@code ReplenishmentRepositorySPI}'s job. */
public interface InventoryRepositorySPI {
	Product addAmount(Location location, String name, ProductType type, int delta);

	/**
	 * Deducts all quantities at {@code location} in one transaction. Safe against concurrent callers: rows are
	 * locked, so two of them can never both take the last item. See {@link OnShortage} for what happens on a
	 * shortage.
	 */
	StockDeduction deductAll(Location location, SortedMap<String, Integer> quantitiesByName, OnShortage onShortage);

	List<Product> findAll(Location location);

	List<LocationStock> findAllLocations();
}
