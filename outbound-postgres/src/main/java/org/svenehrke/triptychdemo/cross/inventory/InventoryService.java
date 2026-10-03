package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

/**
 * Every stock change reads its row with {@code SELECT ... FOR UPDATE} (see {@link StockEntity}), so
 * concurrent changes to the same product wait for each other instead of overwriting each other.
 */
@ApplicationScoped
public class InventoryService implements InventoryRepositorySPI {

    @Override
    @Transactional
    public Product addAmount(Location location, String name, ProductType type, int delta) {
        StockEntity entity = StockEntity.findForUpdate(location, name, type)
            .orElseGet(() -> StockEntity.create(location, name, type));
        entity.availableAmount += delta;
        return entity.toDomain();
    }

    /** Locks the rows in the map's (sorted) order, so concurrent calls cannot deadlock. */
    @Override
    @Transactional
    public StockDeduction deductAll(Location location, SortedMap<String, Integer> quantitiesByName, OnShortage onShortage) {
        var updated = new ArrayList<Product>();
        var shortages = new ArrayList<Shortage>();
        quantitiesByName.forEach((name, quantity) -> {
            StockEntity entity = StockEntity.findByNameForUpdate(location, name).orElse(null);
            int available = entity == null ? 0 : entity.availableAmount;
            if (available < quantity) shortages.add(new Shortage(name, quantity, available));
            if (entity != null) {
                entity.availableAmount = Math.max(0, available - quantity);
                updated.add(entity.toDomain());
            }
        });
        if (onShortage == OnShortage.REJECT && !shortages.isEmpty()) {
            QuarkusTransaction.setRollbackOnly();
            return new StockDeduction(List.of(), shortages);
        }
        return new StockDeduction(updated, shortages);
    }

    @Override
    public List<Product> findAll(Location location) {
        return StockEntity.<StockEntity>list("locationId", location.id()).stream()
                .map(StockEntity::toDomain)
                .toList();
    }

    @Override
    public List<LocationStock> findAllLocations() {
        return StockEntity.<StockEntity>listAll().stream()
                .map(e -> new LocationStock(e.location(), e.toDomain()))
                .toList();
    }
}
