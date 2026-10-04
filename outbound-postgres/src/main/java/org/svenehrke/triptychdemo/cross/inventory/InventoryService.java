package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.reorder.ReorderPolicy;
import org.svenehrke.triptychdemo.cross.replenishment.ShipmentEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /** Locks the rows in the map's (sorted) order, like {@link #deductAll}. */
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordDemand(Replenished location, SortedMap<String, Integer> quantitiesByName) {
        quantitiesByName.forEach((name, quantity) -> {
            var entity = StockEntity.findByNameForUpdate(location, name)
                .or(() -> dcType(name).map(type -> StockEntity.create(location, name, type)))
                .orElse(null);
            if (entity != null) entity.periodDemand += quantity;
        });
    }

    /**
     * Not {@code @Transactional}: each row gets a transaction of its own, so this never holds two locks and cannot
     * deadlock with a sale or a transfer (or block them for the whole run).
     */
    @Override
    public int closePeriod() {
        var dcProducts = QuarkusTransaction.requiringNew().call(() ->
            StockEntity.<StockEntity>list("locationId", Locations.DC.id()).stream()
                .map(e -> new Key(e.name, e.type)).toList());
        int rows = 0;
        for (var location : Locations.ALL) {
            for (var product : dcProducts) {
                QuarkusTransaction.requiringNew().run(() -> learn(location, product));
                rows++;
            }
        }
        return rows;
    }

    private record Key(String name, ProductType type) {}

    private static void learn(Location location, Key product) {
        var policy = ReorderPolicy.of(location);
        var entity = StockEntity.findForUpdate(location, product.name(), product.type())
            .orElseGet(() -> StockEntity.create(location, product.name(), product.type()));
        entity.learned(entity.estimate().next(entity.periodDemand, policy), policy);
        entity.periodDemand = 0;
    }

    @Override
    public List<Product> findAll(Location location) {
        return StockEntity.<StockEntity>list("locationId", location.id()).stream()
                .map(StockEntity::toDomain)
                .toList();
    }

    @Override
    public List<LocationStock> findAllLocations() {
        var inTransit = ShipmentEntity.inTransitByLocationAndProduct();
        return StockEntity.<StockEntity>listAll().stream()
                .map(e -> e.toLocationStock(inTransit.getOrDefault(new ShipmentEntity.Key(e.locationId, e.name), 0)))
                .toList();
    }

    private static Optional<ProductType> dcType(String name) {
        return StockEntity.<StockEntity>find("locationId = ?1 and name = ?2", Locations.DC.id(), name)
            .firstResultOptional().map(e -> e.type);
    }
}
