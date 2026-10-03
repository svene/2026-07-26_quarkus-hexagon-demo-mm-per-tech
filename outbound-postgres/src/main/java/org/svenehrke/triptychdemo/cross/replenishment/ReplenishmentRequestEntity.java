package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.location.Locations;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "replenishment_request")
public class ReplenishmentRequestEntity extends PanacheEntity {

    public String locationId;

    public String productName;

    public int requested;

    public int delivered;

    @Enumerated(EnumType.STRING)
    public RequestStatus status;

    public Instant createdAt;

    public static ReplenishmentRequestEntity create(StockRequest request) {
        var entity = new ReplenishmentRequestEntity();
        entity.locationId = request.location().id();
        entity.productName = request.productName();
        entity.requested = request.quantity();
        entity.status = RequestStatus.PENDING;
        entity.createdAt = Instant.now();
        entity.persist();
        return entity;
    }

    public static boolean anyPending(String productName) {
        return count("productName = ?1 and status = ?2", productName, RequestStatus.PENDING) > 0;
    }

    /** Oldest first, the order in which they are served. */
    public static List<ReplenishmentRequestEntity> findPendingForUpdate(String productName) {
        return find("productName = ?1 and status = ?2", Sort.by("id"), productName, RequestStatus.PENDING)
            .withLock(LockModeType.PESSIMISTIC_WRITE).list();
    }

    public static Optional<ReplenishmentRequestEntity> findPendingForUpdate(long id) {
        return find("id = ?1 and status = ?2", id, RequestStatus.PENDING)
            .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    /** Without loading (and so without caching) the entity, so it can be locked afterwards. */
    public static Optional<String> productNameOf(long id) {
        return getEntityManager()
            .createQuery("select r.productName from ReplenishmentRequestEntity r where r.id = :id", String.class)
            .setParameter("id", id)
            .getResultStream().findFirst();
    }

    public int outstanding() {
        return requested - delivered;
    }

    public ReplenishmentRequest toDomain() {
        return new ReplenishmentRequest(id, Locations.replenishedOf(locationId), productName, requested, delivered, status, createdAt);
    }
}
