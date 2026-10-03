package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.products.ProductStock;

import java.util.List;

/** One row of the admin's product × location matrix: {@code amounts} in the order of the matrix columns. */
public record StockRowVM(String name, String type, List<Integer> amounts) {

    static StockRowVM of(ProductStock stock, List<Location> columns) {
        return new StockRowVM(stock.name(), stock.type().name(), columns.stream().map(stock::availableAt).toList());
    }
}
