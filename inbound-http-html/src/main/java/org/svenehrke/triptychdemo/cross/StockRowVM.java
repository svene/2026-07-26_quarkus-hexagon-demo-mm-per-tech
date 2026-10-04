package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.products.ProductStock;

import java.util.List;

/**
 * One row of the admin's product × location matrix: {@code amounts}, {@code inTransit} and {@code levels} in the order
 * of the matrix columns; a level is null where the location has no row for the product.
 */
public record StockRowVM(String name, String type, List<Integer> amounts, List<Integer> inTransit, List<LevelsVM> levels) {

    static StockRowVM of(ProductStock stock, List<Location> columns) {
        return new StockRowVM(stock.name(), stock.type().name(),
            columns.stream().map(stock::availableAt).toList(),
            columns.stream().map(stock::inTransitTo).toList(),
            columns.stream().map(l -> stock.levelsAt(l).map(LevelsVM::of).orElse(null)).toList());
    }
}
