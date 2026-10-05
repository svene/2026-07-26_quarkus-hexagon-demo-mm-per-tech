package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.products.CatalogProduct;

/** {@code type}: the {@code ProductType} name, which picks the order form the product appears in. */
public record CatalogProductVM(String name, String type) {

    static CatalogProductVM of(CatalogProduct product) {
        return new CatalogProductVM(product.name(), product.type().name());
    }
}
