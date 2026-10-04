package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.reorder.DemandEstimate;
import org.svenehrke.triptychdemo.cross.reorder.LearnedLevels;
import org.svenehrke.triptychdemo.cross.reorder.ReorderPolicy;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Optional;

/**
 * One product's stock at one location, with its learned demand and levels; a new row starts with the cold-start
 * estimate of its location's {@link ReorderPolicy}.
 */
@Entity
@Table(name = "stock", uniqueConstraints = @UniqueConstraint(columnNames = {"locationId", "name", "type"}))
public class StockEntity extends PanacheEntity {

    public String locationId;

    public String name;

    @Enumerated(EnumType.STRING)
    public ProductType type;

    public int availableAmount;

    /** What customers asked for in the current demand period - at the DC: what the other locations requested. */
    public int periodDemand;

    public double avgDemand;

    public double demandVar;

    /** Not {@code min}/{@code max}: SQL keywords. */
    public int minLevel;

    public int maxLevel;

    public static StockEntity create(Location location, String name, ProductType type) {
        var entity = new StockEntity();
        entity.locationId = location.id();
        entity.name = name;
        entity.type = type;
        var policy = ReorderPolicy.of(location);
        entity.learned(DemandEstimate.initial(policy), policy);
        entity.persist();
        return entity;
    }

    public static Optional<StockEntity> findForUpdate(Location location, String name, ProductType type) {
        return find("locationId = ?1 and name = ?2 and type = ?3", location.id(), name, type)
            .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public static Optional<StockEntity> findByNameForUpdate(Location location, String name) {
        return find("locationId = ?1 and name = ?2", location.id(), name)
            .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public Location location() {
        return Locations.of(locationId);
    }

    public DemandEstimate estimate() {
        return new DemandEstimate(avgDemand, demandVar);
    }

    public LearnedLevels levels() {
        return new LearnedLevels(minLevel, maxLevel);
    }

    /** Stores {@code estimate} and the levels derived from it. */
    public void learned(DemandEstimate estimate, ReorderPolicy policy) {
        var levels = LearnedLevels.of(estimate, policy);
        avgDemand = estimate.avg();
        demandVar = estimate.var();
        minLevel = levels.min();
        maxLevel = levels.max();
    }

    public Product toDomain() {
        return new Product(name, type, availableAmount);
    }

    public LocationStock toLocationStock() {
        return new LocationStock(location(), toDomain(), estimate(), levels());
    }
}
