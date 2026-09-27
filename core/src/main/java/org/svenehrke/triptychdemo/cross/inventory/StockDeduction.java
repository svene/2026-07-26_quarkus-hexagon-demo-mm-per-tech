package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.products.Product;

import java.util.List;

/**
 * Result of {@link InventoryRepositorySPI#deductAll}: the products whose stock changed, and every product
 * that had less stock than requested. With {@link OnShortage#REJECT} and any shortage, {@code updated} is empty.
 */
public record StockDeduction(List<Product> updated, List<Shortage> shortages) {}
