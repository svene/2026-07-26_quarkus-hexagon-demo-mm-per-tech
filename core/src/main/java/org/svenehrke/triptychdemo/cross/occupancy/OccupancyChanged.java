package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.location.Store;

/**
 * A newer occupancy of {@code store} was stored; fired with {@code Event.fireAsync}, like the
 * {@code InventoryEvent}s. Not one of them: occupancy is no stock, nothing in core reacts to it - only the live
 * updates of {@code /locations}.
 */
public record OccupancyChanged(Store store) {}
