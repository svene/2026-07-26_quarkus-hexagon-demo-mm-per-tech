package org.svenehrke.triptychdemo.cross.inventory;

import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Optional;

/** One product's stock at one location. */
@Entity
@Table(name = "stock", uniqueConstraints = @UniqueConstraint(columnNames = {"locationId", "name", "type"}))
public class StockEntity extends PanacheEntity {

    public String locationId;

    public String name;

    @Enumerated(EnumType.STRING)
    public ProductType type;

    public int availableAmount;

    public static StockEntity create(Location location, String name, ProductType type) {
        var entity = new StockEntity();
        entity.locationId = location.id();
        entity.name = name;
        entity.type = type;
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

    public Product toDomain() {
        return new Product(name, type, availableAmount);
    }
}
