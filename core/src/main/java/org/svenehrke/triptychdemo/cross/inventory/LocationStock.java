package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.products.Product;

/** One product's stock at one location. */
public record LocationStock(Location location, Product product) {}
