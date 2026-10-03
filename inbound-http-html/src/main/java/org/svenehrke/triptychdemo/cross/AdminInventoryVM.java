package org.svenehrke.triptychdemo.cross;

import java.util.List;

/** The product × location matrix; {@code locations} are its columns, DC first. */
public record AdminInventoryVM(List<LocationVM> locations, List<StockRowVM> products) {}
