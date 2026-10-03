package org.svenehrke.triptychdemo.cross;

import java.util.List;

/** Every store and the online FC, in {@code Locations.REPLENISHED} order. */
public record LocationsPageVM(List<LocationInventoryVM> locations) {}
