package org.svenehrke.triptychdemo.cross.products;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.inventory.LocationStock;
import org.svenehrke.triptychdemo.cross.location.Location;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ProductsHandler {

    @Inject
    InventoryRepositorySPI inventoryRepository;

    /** The products {@code location} has a stock row for (possibly 0). */
    public List<Product> listAll(Location location) {
        return inventoryRepository.findAll(location);
    }

    /**
     * Every product any location has a stock row for - in practice what the DC has ever carried, since stock
     * reaches the other locations only from the DC - with its stock at each location.
     */
    public List<ProductStock> listAllLocations() {
        record Key(String name, ProductType type) {}
        Map<Key, Map<Location, LocationStock>> byProduct = new LinkedHashMap<>();
        for (LocationStock stock : inventoryRepository.findAllLocations()) {
            var product = stock.product();
            byProduct.computeIfAbsent(new Key(product.name(), product.type()), k -> new HashMap<>())
                .put(stock.location(), stock);
        }
        return byProduct.entrySet().stream()
            .map(e -> new ProductStock(e.getKey().name(), e.getKey().type(), e.getValue()))
            .toList();
    }
}
