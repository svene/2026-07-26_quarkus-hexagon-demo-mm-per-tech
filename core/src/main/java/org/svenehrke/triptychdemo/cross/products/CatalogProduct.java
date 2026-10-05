package org.svenehrke.triptychdemo.cross.products;

/** A product the supermarket chain lists, see {@link Catalog}. */
public record CatalogProduct(String name, ProductType type) {}
