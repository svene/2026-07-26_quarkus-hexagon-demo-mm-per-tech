package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "supplier_order")
public class SupplierOrderEntity extends PanacheEntity {

    public String productName;

    @Enumerated(EnumType.STRING)
    public ProductType type;

    public int quantity;

    public int delivered;

    @Convert(converter = StatusConverter.class)
    public SupplierOrderStatus status;

    @Convert(converter = OriginConverter.class)
    public SupplierOrderOrigin origin;

    public Instant createdAt;

    public static SupplierOrderEntity create(String productName, ProductType type, int quantity, SupplierOrderOrigin origin) {
        var entity = new SupplierOrderEntity();
        entity.productName = productName;
        entity.type = type;
        entity.quantity = quantity;
        entity.status = SupplierOrderStatus.OPEN;
        entity.origin = origin;
        entity.createdAt = Instant.now();
        entity.persist();
        return entity;
    }

    /** What the open orders of {@code productName} will still deliver. */
    public static int outstanding(String productName) {
        return getEntityManager()
            .createQuery("select coalesce(sum(o.quantity - o.delivered), 0) from SupplierOrderEntity o"
                + " where o.productName = :productName and o.status = :status", Long.class)
            .setParameter("productName", productName)
            .setParameter("status", SupplierOrderStatus.OPEN)
            .getSingleResult().intValue();
    }

    /** Oldest first: the order a delivery closes them in. */
    public static List<SupplierOrderEntity> findOpenForUpdate(String productName) {
        return find("productName = ?1 and status = ?2", Sort.by("id"), productName, SupplierOrderStatus.OPEN)
            .withLock(LockModeType.PESSIMISTIC_WRITE).list();
    }

    public static Optional<SupplierOrderEntity> findOpenForUpdate(long id) {
        return find("id = ?1 and status = ?2", id, SupplierOrderStatus.OPEN)
            .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    /** Without loading (and so without caching) the entity, so it can be locked afterwards. */
    public static Optional<String> productNameOf(long id) {
        return getEntityManager()
            .createQuery("select o.productName from SupplierOrderEntity o where o.id = :id", String.class)
            .setParameter("id", id)
            .getResultStream().findFirst();
    }

    public int outstanding() {
        return quantity - delivered;
    }

    public SupplierOrder toDomain() {
        return new SupplierOrder(id, productName, type, quantity, delivered, status, origin, createdAt);
    }

    @Converter
    static class StatusConverter implements AttributeConverter<SupplierOrderStatus, String> {
        @Override public String convertToDatabaseColumn(SupplierOrderStatus status) { return status.name(); }
        @Override public SupplierOrderStatus convertToEntityAttribute(String name) { return SupplierOrderStatus.of(name); }
    }

    @Converter
    static class OriginConverter implements AttributeConverter<SupplierOrderOrigin, String> {
        @Override public String convertToDatabaseColumn(SupplierOrderOrigin origin) { return origin.name(); }
        @Override public SupplierOrderOrigin convertToEntityAttribute(String name) { return SupplierOrderOrigin.of(name); }
    }
}
