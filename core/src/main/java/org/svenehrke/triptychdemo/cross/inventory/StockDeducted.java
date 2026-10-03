package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;

/** A sale (online checkout or cashpoint) took stock from {@code location}. */
public record StockDeducted(Location location) implements InventoryEvent {}
