package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Entity
@Table(name = "shipment")
public class ShipmentEntity extends PanacheEntity {

    public long requestId;

    public String locationId;

    public String productName;

    @Enumerated(EnumType.STRING)
    public ProductType type;

    public int quantity;

    @Enumerated(EnumType.STRING)
    public ShipmentStatus status;

    public Instant dispatchedAt;

    public Instant arrivedAt;

    public static ShipmentEntity create(ReplenishmentRequestEntity request, ProductType type, int quantity) {
        var entity = new ShipmentEntity();
        entity.requestId = request.id;
        entity.locationId = request.locationId;
        entity.productName = request.productName;
        entity.type = type;
        entity.quantity = quantity;
        entity.status = ShipmentStatus.IN_TRANSIT;
        entity.dispatchedAt = Instant.now();
        entity.persist();
        return entity;
    }

    public static Optional<ShipmentEntity> findInTransitForUpdate(long id) {
        return find("id = ?1 and status = ?2", id, ShipmentStatus.IN_TRANSIT)
            .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public static List<ShipmentEntity> findInTransit(Instant dispatchedBefore) {
        return list("status = ?1 and dispatchedAt < ?2", Sort.by("id"), ShipmentStatus.IN_TRANSIT, dispatchedBefore);
    }

    /** What is on its way to {@code locationId} of {@code productName}. */
    public static int inTransit(String locationId, String productName) {
        return getEntityManager()
            .createQuery("select coalesce(sum(s.quantity), 0) from ShipmentEntity s"
                + " where s.locationId = :locationId and s.productName = :productName and s.status = :status", Long.class)
            .setParameter("locationId", locationId)
            .setParameter("productName", productName)
            .setParameter("status", ShipmentStatus.IN_TRANSIT)
            .getSingleResult().intValue();
    }

    public record Key(String locationId, String productName) {}

    /** What is on its way, per location and product. */
    public static Map<Key, Integer> inTransitByLocationAndProduct() {
        return getEntityManager()
            .createQuery("select s.locationId, s.productName, sum(s.quantity) from ShipmentEntity s"
                + " where s.status = :status group by s.locationId, s.productName", Object[].class)
            .setParameter("status", ShipmentStatus.IN_TRANSIT)
            .getResultStream()
            .collect(Collectors.toMap(row -> new Key((String) row[0], (String) row[1]), row -> ((Number) row[2]).intValue()));
    }

    public Replenished location() {
        return Locations.replenishedOf(locationId);
    }

    public Shipment toDomain() {
        return new Shipment(id, requestId, location(), productName, quantity, status, dispatchedAt, arrivedAt);
    }
}
