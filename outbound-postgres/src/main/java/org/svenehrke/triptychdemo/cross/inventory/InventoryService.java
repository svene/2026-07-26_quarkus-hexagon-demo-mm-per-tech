package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.reorder.ReorderPolicy;
import org.svenehrke.triptychdemo.cross.replenishment.ShipmentTable;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

/**
 * Every stock change reads its row with {@code SELECT ... FOR UPDATE} (see {@link StockTable}), so
 * concurrent changes to the same product wait for each other instead of overwriting each other.
 */
@ApplicationScoped
public class InventoryService implements InventoryRepositorySPI {

    @Inject
    StockTable stockTable;

    @Inject
    ShipmentTable shipments;

    @Override
    @Transactional
    public Product addAmount(Location location, String name, ProductType type, int delta) {
        var row = stockTable.findOrCreateForUpdate(location, name, type);
        return stockTable.addAvailable(row.id(), delta).toDomain();
    }

    /** Locks the rows in the map's (sorted) order, so concurrent calls cannot deadlock. */
    @Override
    @Transactional
    public StockDeduction deductAll(Location location, SortedMap<String, Integer> quantitiesByName, OnShortage onShortage) {
        var updated = new ArrayList<Product>();
        var shortages = new ArrayList<Shortage>();
        quantitiesByName.forEach((name, quantity) -> {
            var row = stockTable.findByNameForUpdate(location, name).orElse(null);
            int available = row == null ? 0 : row.availableAmount();
            if (available < quantity) shortages.add(new Shortage(name, quantity, available));
            if (row != null) {
                updated.add(stockTable.setAvailable(row.id(), Math.max(0, available - quantity)).toDomain());
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
            stockTable.findByNameForUpdate(location, name)
                .or(() -> stockTable.findType(Locations.DC, name).map(type -> stockTable.create(location, name, type)))
                .ifPresent(row -> stockTable.addPeriodDemand(row.id(), quantity));
        });
    }

    /**
     * Not {@code @Transactional}: each row gets a transaction of its own, so this never holds two locks and cannot
     * deadlock with a sale or a transfer (or block them for the whole run).
     */
    @Override
    public int closePeriod() {
        var dcProducts = QuarkusTransaction.requiringNew().call(() ->
            stockTable.findAll(Locations.DC).stream()
                .map(row -> new Key(row.name(), row.type())).toList());
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

    private void learn(Location location, Key product) {
        var policy = ReorderPolicy.of(location);
        var row = stockTable.findOrCreateForUpdate(location, product.name(), product.type());
        stockTable.closePeriod(row.id(), row.estimate().next(row.periodDemand(), policy), policy);
    }

    @Override
    public List<Product> findAll(Location location) {
        return stockTable.findAll(location).stream()
                .map(StockRow::toDomain)
                .toList();
    }

    @Override
    public List<LocationStock> findAllLocations() {
        var inTransit = shipments.inTransitByLocationAndProduct();
        return stockTable.findAll().stream()
                .map(row -> row.toLocationStock(inTransit.getOrDefault(new ShipmentTable.Key(row.locationId(), row.name()), 0)))
                .toList();
    }
}
