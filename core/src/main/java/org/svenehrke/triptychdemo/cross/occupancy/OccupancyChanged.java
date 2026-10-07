package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.location.Store;

/**
 * A newer occupancy of {@code store} was stored; fired via {@code AsyncEvents} ({@code Event.fireAsync}), like the
 * {@code InventoryEvent}s. Not one of them: occupancy is no stock. Reacted to by the live updates of
 * {@code /locations} and the automatic tills ({@code AutoTillsReceiver}).
 */
public record OccupancyChanged(Store store) {}
