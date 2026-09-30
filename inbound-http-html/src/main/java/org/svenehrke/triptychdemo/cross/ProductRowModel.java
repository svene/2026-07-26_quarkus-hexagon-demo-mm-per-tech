package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.products.Product;

public record ProductRowModel(String name, String type, int availableAmount) {

    static ProductRowModel of(Product product) {
        return new ProductRowModel(product.name(), product.type().name(), product.availableAmount());
    }
}
