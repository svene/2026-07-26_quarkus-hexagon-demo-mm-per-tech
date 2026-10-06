package org.svenehrke.triptychdemo.cross.occupancy;

import java.util.List;

public interface OccupancyRepositorySPI {

	/** Stores {@code occupancy} unless the stored one of its store is as new or newer; true if it was stored. */
	boolean saveIfNewer(StoreOccupancy occupancy);

	/** The latest occupancy of each store that has reported one. */
	List<StoreOccupancy> findAll();
}
