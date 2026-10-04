package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.reorder.DemandEstimate;
import org.svenehrke.triptychdemo.cross.reorder.LearnedLevels;

/**
 * One product's stock at one location, with its learned demand and reorder levels; {@code inTransit} is what the DC
 * has shipped to it that has not arrived yet (always 0 at the DC).
 */
public record LocationStock(Location location, Product product, DemandEstimate estimate, LearnedLevels levels, int inTransit) {}
