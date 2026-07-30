package com.example.hexademo.adapter.outbound.postgres.inventory;

import com.example.hexademo.core.domain.Product;
import com.example.hexademo.core.domain.ProductType;
import com.example.hexademo.core.port.out.InventoryRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class InventoryService implements InventoryRepositorySPI {

    @Override
    @Transactional
    public Product addAmount(String name, ProductType type, int delta) {
        ProductEntity entity = ProductEntity.findByNameAndType(name, type).orElse(null);
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

    @Override
    @Transactional
    public Product deductAmount(String name, int delta) {
        ProductEntity entity = ProductEntity.findByName(name).orElse(null);
        if (entity == null) return null;
        entity.availableAmount = Math.max(0, entity.availableAmount - delta);
        return entity.toDomain();
    }

    @Override
    public List<Product> findAll() {
        return ProductEntity.<ProductEntity>listAll().stream()
                .map(ProductEntity::toDomain)
                .toList();
    }
}
