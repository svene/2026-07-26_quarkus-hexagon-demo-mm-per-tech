package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

/**
 * Every stock change reads its row with {@code SELECT ... FOR UPDATE} (see {@link ProductEntity}), so
 * concurrent changes to the same product wait for each other instead of overwriting each other.
 */
@ApplicationScoped
public class InventoryService implements InventoryRepositorySPI {

    @Override
    @Transactional
    public Product addAmount(String name, ProductType type, int delta) {
        ProductEntity entity = ProductEntity.findByNameAndTypeForUpdate(name, type).orElse(null);
        if (entity == null) {
            entity = new ProductEntity();
            entity.name = name;
            entity.type = type;
            entity.availableAmount = delta;
            entity.persist();
        } else {
            entity.availableAmount += delta;
        }
        return entity.toDomain();
    }

    /** Locks the rows in the map's (sorted) order, so concurrent calls cannot deadlock. */
    @Override
    @Transactional
    public StockDeduction deductAll(SortedMap<String, Integer> quantitiesByName, OnShortage onShortage) {
        var updated = new ArrayList<Product>();
        var shortages = new ArrayList<Shortage>();
        quantitiesByName.forEach((name, quantity) -> {
            ProductEntity entity = ProductEntity.findByNameForUpdate(name).orElse(null);
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
    public List<Product> findAll() {
        return ProductEntity.<ProductEntity>listAll().stream()
                .map(ProductEntity::toDomain)
                .toList();
    }
}
