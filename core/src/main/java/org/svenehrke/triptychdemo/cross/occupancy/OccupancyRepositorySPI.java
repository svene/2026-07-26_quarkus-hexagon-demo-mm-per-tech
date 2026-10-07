package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.location.Store;

import java.time.Instant;
import java.util.List;

public interface OccupancyRepositorySPI {

	/** Stores {@code occupancy} unless the stored one of its store is as new or newer; true if it was stored. */
	boolean saveIfNewer(StoreOccupancy occupancy);

	/** The latest occupancy of each store that has reported one. */
	List<StoreOccupancy> findAll();

	/**
	 * Adds {@code occupancy} to its store's history unless a report of the same time is there already (idempotent),
	 * and drops the store's reports measured before {@code keepSince}.
	 */
	void appendToHistory(StoreOccupancy occupancy, Instant keepSince);

	/** The store's reports measured at or after {@code since}, oldest first. */
	List<StoreOccupancy> history(Store store, Instant since);
}
