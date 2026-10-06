package org.svenehrke.triptychdemo.cross;

import java.util.List;

/** Every store and the online FC, in {@code Locations.REPLENISHED} order; the occupancy of each store. */
public record LocationsPageVM(List<LocationInventoryVM> locations, List<StoreOccupancyVM> occupancy) {}
