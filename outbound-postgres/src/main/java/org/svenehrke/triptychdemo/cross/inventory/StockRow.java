package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.reorder.DemandEstimate;
import org.svenehrke.triptychdemo.cross.reorder.LearnedLevels;

/**
 * One row of the {@code stock} table (see {@link StockTable}): one product's stock at one location, with its learned
 * demand and levels. A snapshot - a change is written with {@link StockTable}, which returns the row afterwards.
 *
 * @param periodDemand what customers asked for in the current demand period - at the DC: what the other locations
 *                     requested
 * @param minLevel     not {@code min}/{@code max}: SQL keywords
 */
public record StockRow(long id, String locationId, String name, ProductType type, int availableAmount, int periodDemand,
                       double avgDemand, double demandVar, int minLevel, int maxLevel) {

    public Location location() {
        return Locations.of(locationId);
    }

    public DemandEstimate estimate() {
        return new DemandEstimate(avgDemand, demandVar);
    }

    public LearnedLevels levels() {
        return new LearnedLevels(minLevel, maxLevel);
    }

    public Product toDomain() {
        return new Product(name, type, availableAmount);
    }

    public LocationStock toLocationStock(int inTransit) {
        return new LocationStock(location(), toDomain(), estimate(), levels(), inTransit);
    }
}
