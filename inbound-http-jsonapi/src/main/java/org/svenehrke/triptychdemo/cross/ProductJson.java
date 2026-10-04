package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.products.Product;

import java.util.List;

/**
 * A product as the JSON API returns it ({@code {"name":…,"type":"FRUIT","availableAmount":…}}). Core's
 * {@link Product} isn't serialized directly, so a change inside core can't silently change the API.
 */
public record ProductJson(String name, String type, int availableAmount) {

    static List<ProductJson> of(List<Product> products) {
        return products.stream().map(p -> new ProductJson(p.name(), p.type().name(), p.availableAmount())).toList();
    }
}
