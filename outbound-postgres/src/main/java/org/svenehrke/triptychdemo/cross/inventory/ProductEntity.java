package org.svenehrke.triptychdemo.cross.inventory;

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

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "type"}))
public class ProductEntity extends PanacheEntity {

    public String name;

    @Enumerated(EnumType.STRING)
    public ProductType type;

    public int availableAmount;

    public static Optional<ProductEntity> findByNameAndTypeForUpdate(String name, ProductType type) {
        return find("name = ?1 and type = ?2", name, type).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public static Optional<ProductEntity> findByNameForUpdate(String name) {
        return find("name", name).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public Product toDomain() {
        return new Product(name, type, availableAmount);
    }
}
