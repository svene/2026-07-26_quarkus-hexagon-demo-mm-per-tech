package org.svenehrke.triptychdemo.cross.products;

import org.svenehrke.triptychdemo.cross.location.Location;

import java.util.Map;

/** One product's stock at every location; a location without a row for it has 0. */
public record ProductStock(String name, ProductType type, Map<Location, Integer> availableByLocation) {

	public ProductStock {
		availableByLocation = Map.copyOf(availableByLocation);
	}

	public int availableAt(Location location) {
		return availableByLocation.getOrDefault(location, 0);
	}
}
