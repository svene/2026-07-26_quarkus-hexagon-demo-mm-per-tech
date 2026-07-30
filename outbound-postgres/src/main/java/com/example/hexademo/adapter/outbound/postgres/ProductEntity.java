package com.example.hexademo.adapter.outbound.postgres;

import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.ProductType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
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

    public static Optional<ProductEntity> findByNameAndType(String name, ProductType type) {
        return find("name = ?1 and type = ?2", name, type).firstResultOptional();
    }

    public static Optional<ProductEntity> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    public Product toDomain() {
        return new Product(name, type, availableAmount);
    }
}
