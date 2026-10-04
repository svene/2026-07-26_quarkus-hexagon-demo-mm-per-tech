package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.inventory.StockEntity;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.location.Locations;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every method that touches a product's requests locks that product's DC stock row first, so all of them are
 * serialized per product; then the requests, then the target location's row (see the SPI for why). Every request
 * created adds its quantity to the DC row's period demand: what the DC learns its levels from.
 * <p>
 * A transfer takes the stock off the DC and records a {@link ShipmentEntity} in transit; {@link #receiveShipment}
 * adds it to the location's stock. That one locks the shipment, then the location's row - never the DC row, so it
 * cannot deadlock with the others.
 */
@ApplicationScoped
public class ReplenishmentService implements ReplenishmentRepositorySPI {

    @Override
    @Transactional
    public Optional<Requested> request(StockRequest request) {
        var dcStock = StockEntity.findByNameForUpdate(Locations.DC, request.productName()).orElse(null);
        if (dcStock == null) return Optional.empty();
        var entity = ReplenishmentRequestEntity.create(request, RequestOrigin.MANUAL);
        dcStock.periodDemand += request.quantity();
        var transfers = allocate(dcStock);
        return Optional.of(new Requested(entity.toDomain(), transfers));
    }

    /**
     * The location's row is locked after the DC row (the lock order of {@link #transfer}); with the DC row locked, no
     * other request of the product can be stored concurrently, so the outstanding sum stays valid until the new
     * request is stored. With the location's row locked, no shipment can arrive concurrently either
     * ({@link #receiveShipment} locks it, too), so {@code available} and the in-transit sum are consistent.
     */
    @Override
    @Transactional
    public Optional<ReplenishmentRequest> requestIfLow(Replenished location, String productName) {
        var dcStock = StockEntity.findByNameForUpdate(Locations.DC, productName).orElse(null);
        if (dcStock == null) return Optional.empty();
        var stock = StockEntity.findForUpdate(location, productName, dcStock.type)
            .orElseGet(() -> StockEntity.create(location, productName, dcStock.type));
        int quantity = stock.levels().reorderQuantity(stock.availableAmount,
            ShipmentEntity.inTransit(location.id(), productName) + ReplenishmentRequestEntity.outstanding(location.id(), productName));
        if (quantity <= 0) return Optional.empty();
        var request = new StockRequest(location, productName, Math.min(quantity, StockRequest.MAX_QUANTITY));
        dcStock.periodDemand += request.quantity();
        return Optional.of(ReplenishmentRequestEntity.create(request, RequestOrigin.AUTOMATIC).toDomain());
    }

    @Override
    @Transactional
    public List<Transfer> allocate(String productName) {
        return StockEntity.findByNameForUpdate(Locations.DC, productName)
            .map(ReplenishmentService::allocate)
            .orElse(List.of());
    }

    @Override
    @Transactional
    public Optional<Requested> fulfil(long requestId) {
        return lockDcStockAndPendingRequest(requestId).map(locked -> {
            int quantity = Math.min(locked.dcStock().availableAmount, locked.request().outstanding());
            var transfers = quantity == 0
                ? List.<Transfer>of()
                : List.of(transfer(locked.dcStock(), locked.request(), quantity));
            return new Requested(locked.request().toDomain(), transfers);
        });
    }

    @Override
    @Transactional
    public Optional<ReplenishmentRequest> reject(long requestId) {
        return lockDcStockAndPendingRequest(requestId).map(locked -> {
            locked.request().status = RequestStatus.REJECTED;
            return locked.request().toDomain();
        });
    }

    @Override
    @Transactional
    public Optional<Shipment> receiveShipment(long shipmentId) {
        return ShipmentEntity.findInTransitForUpdate(shipmentId).map(shipment -> {
            var target = StockEntity.findForUpdate(shipment.location(), shipment.productName, shipment.type)
                .orElseGet(() -> StockEntity.create(shipment.location(), shipment.productName, shipment.type));
            target.availableAmount += shipment.quantity;
            shipment.status = ShipmentStatus.ARRIVED;
            shipment.arrivedAt = Instant.now();
            return shipment.toDomain();
        });
    }

    @Override
    public List<Shipment> findInTransit(Instant dispatchedBefore) {
        return ShipmentEntity.findInTransit(dispatchedBefore).stream().map(ShipmentEntity::toDomain).toList();
    }

    @Override
    public List<ReplenishmentRequest> findPending() {
        return ReplenishmentRequestEntity.<ReplenishmentRequestEntity>list("status", Sort.by("id"), RequestStatus.PENDING)
            .stream().map(ReplenishmentRequestEntity::toDomain).toList();
    }

    @Override
    public List<ReplenishmentRequest> findRecent(Replenished location, int limit) {
        return ReplenishmentRequestEntity.<ReplenishmentRequestEntity>find("locationId", Sort.descending("id"), location.id())
            .page(0, limit).list()
            .stream().map(ReplenishmentRequestEntity::toDomain).toList();
    }

    /**
     * Shares the (locked) DC stock among the pending requests of its product, see {@link FairShare}; requests are
     * locked oldest first, their target rows in the same order.
     */
    private static List<Transfer> allocate(StockEntity dcStock) {
        var pending = ReplenishmentRequestEntity.findPendingForUpdate(dcStock.name);
        var shares = FairShare.allocate(dcStock.availableAmount,
            pending.stream().map(ReplenishmentRequestEntity::outstanding).toList());
        var transfers = new ArrayList<Transfer>();
        for (int i = 0; i < pending.size(); i++) {
            if (shares.get(i) == 0) continue;
            transfers.add(transfer(dcStock, pending.get(i), shares.get(i)));
        }
        return transfers;
    }

    private record Locked(StockEntity dcStock, ReplenishmentRequestEntity request) {}

    /** The request's product is read first without a lock, to keep the lock order: DC stock, then the request. */
    private static Optional<Locked> lockDcStockAndPendingRequest(long requestId) {
        return ReplenishmentRequestEntity.productNameOf(requestId)
            .flatMap(productName -> StockEntity.findByNameForUpdate(Locations.DC, productName))
            .flatMap(dcStock -> ReplenishmentRequestEntity.findPendingForUpdate(requestId)
                .map(request -> new Locked(dcStock, request)));
    }

    /**
     * Ships {@code quantity} (more than 0, at most what the DC has and the request still needs) to the request's
     * location. Its stock row is created already (locked, in the lock order), so the location shows what is in transit
     * to it even before the first arrival.
     */
    private static Transfer transfer(StockEntity dcStock, ReplenishmentRequestEntity request, int quantity) {
        dcStock.availableAmount -= quantity;
        var location = Locations.replenishedOf(request.locationId);
        if (StockEntity.findForUpdate(location, dcStock.name, dcStock.type).isEmpty()) {
            StockEntity.create(location, dcStock.name, dcStock.type);
        }
        request.shipped += quantity;
        if (request.outstanding() == 0) request.status = RequestStatus.FULFILLED;
        var shipment = ShipmentEntity.create(request, dcStock.type, quantity);
        return new Transfer(request.toDomain(), shipment.toDomain());
    }
}
