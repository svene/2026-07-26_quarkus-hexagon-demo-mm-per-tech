package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.products.Product;

public record ProductRowVM(String name, String type, int availableAmount) {

    static ProductRowVM of(Product product) {
        return new ProductRowVM(product.name(), product.type().name(), product.availableAmount());
    }
}
