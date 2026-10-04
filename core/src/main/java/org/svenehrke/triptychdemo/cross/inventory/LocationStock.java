package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.reorder.DemandEstimate;
import org.svenehrke.triptychdemo.cross.reorder.LearnedLevels;

/** One product's stock at one location, with its learned demand and reorder levels. */
public record LocationStock(Location location, Product product, DemandEstimate estimate, LearnedLevels levels) {}
