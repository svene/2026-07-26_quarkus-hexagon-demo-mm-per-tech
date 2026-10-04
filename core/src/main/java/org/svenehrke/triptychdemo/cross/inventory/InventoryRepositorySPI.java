package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;

import java.util.List;
import java.util.SortedMap;

/**
 * Stock per location; moving stock between locations is {@code ReplenishmentRepositorySPI}'s job, supplier deliveries
 * to the DC are {@code SupplierOrderRepositorySPI}'s. Every stock row also carries its learned demand and reorder levels
 * ({@code cross.reorder}); a new one starts with the cold-start estimate of its location's {@code ReorderPolicy}.
 */
public interface InventoryRepositorySPI {
	Product addAmount(Location location, String name, ProductType type, int delta);

	/**
	 * Deducts all quantities at {@code location} in one transaction. Safe against concurrent callers: rows are
	 * locked, so two of them can never both take the last item. See {@link OnShortage} for what happens on a
	 * shortage.
	 */
	StockDeduction deductAll(Location location, SortedMap<String, Integer> quantitiesByName, OnShortage onShortage);

	/**
	 * Adds the quantities customers asked for to the current period's demand, in a transaction of its own - also
	 * for a sale {@link #deductAll} rejected or capped (a lost sale is still demand). A product the DC has never
	 * carried is ignored; a product the location has no row for yet gets one (available 0).
	 */
	void recordDemand(Replenished location, SortedMap<String, Integer> quantitiesByName);

	/**
	 * Closes the demand period: first gives every store and the online FC a row for every product the DC carries,
	 * then folds each row's period demand into its estimate (the DC's rows included), recalculates its levels and
	 * resets the period demand - one short transaction per row. Returns the number of rows.
	 */
	int closePeriod();

	List<Product> findAll(Location location);

	List<LocationStock> findAllLocations();
}
