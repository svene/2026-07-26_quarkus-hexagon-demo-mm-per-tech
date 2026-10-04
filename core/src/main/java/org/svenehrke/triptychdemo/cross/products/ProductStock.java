package org.svenehrke.triptychdemo.cross.products;

import org.svenehrke.triptychdemo.cross.inventory.LocationStock;
import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.reorder.DemandEstimate;
import org.svenehrke.triptychdemo.cross.reorder.LearnedLevels;

import java.util.Map;
import java.util.Optional;

/** One product's stock at every location; a location without a row for it has 0 and no levels. */
public record ProductStock(String name, ProductType type, Map<Location, LocationStock> byLocation) {

	public ProductStock {
		byLocation = Map.copyOf(byLocation);
	}

	public int availableAt(Location location) {
		var stock = byLocation.get(location);
		return stock == null ? 0 : stock.product().availableAmount();
	}

	public int inTransitTo(Location location) {
		var stock = byLocation.get(location);
		return stock == null ? 0 : stock.inTransit();
	}

	public Optional<DemandEstimate> estimateAt(Location location) {
		return Optional.ofNullable(byLocation.get(location)).map(LocationStock::estimate);
	}

	public Optional<LearnedLevels> levelsAt(Location location) {
		return Optional.ofNullable(byLocation.get(location)).map(LocationStock::levels);
	}
}
