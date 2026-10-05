package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.inventory.StockRow;
import org.svenehrke.triptychdemo.cross.inventory.StockTable;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.location.Locations;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 * A transfer takes the stock off the DC and records a shipment in transit ({@link ShipmentTable}); {@link #receiveShipment}
 * adds it to the location's stock. That one locks the shipment, then the location's row - never the DC row, so it
 * cannot deadlock with the others.
 */
@ApplicationScoped
public class ReplenishmentService implements ReplenishmentRepositorySPI {

    @Inject
    StockTable stockTable;

    @Inject
    ReplenishmentRequestTable requests;

    @Inject
    ShipmentTable shipments;

    /** The new request is read again at the end: the allocation may have shipped to it, too. */
    @Override
    @Transactional
    public Optional<Requested> request(StockRequest request) {
        var dcStock = stockTable.findByNameForUpdate(Locations.DC, request.productName()).orElse(null);
        if (dcStock == null) return Optional.empty();
        var created = requests.create(request, RequestOrigin.MANUAL);
        stockTable.addPeriodDemand(dcStock.id(), request.quantity());
        var transfers = allocate(dcStock);
        return Optional.of(new Requested(requests.find(created.id()).orElseThrow(), transfers));
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
        var dcStock = stockTable.findByNameForUpdate(Locations.DC, productName).orElse(null);
        if (dcStock == null) return Optional.empty();
        var stock = stockTable.findOrCreateForUpdate(location, productName, dcStock.type());
        int quantity = stock.levels().reorderQuantity(stock.availableAmount(),
            shipments.inTransit(location.id(), productName) + requests.outstanding(location.id(), productName));
        if (quantity <= 0) return Optional.empty();
        var request = new StockRequest(location, productName, Math.min(quantity, StockRequest.MAX_QUANTITY));
        stockTable.addPeriodDemand(dcStock.id(), request.quantity());
        return Optional.of(requests.create(request, RequestOrigin.AUTOMATIC));
    }

    @Override
    @Transactional
    public List<Transfer> allocate(String productName) {
        return stockTable.findByNameForUpdate(Locations.DC, productName)
            .map(this::allocate)
            .orElse(List.of());
    }

    @Override
    @Transactional
    public Optional<Requested> fulfil(long requestId) {
        return lockDcStockAndPendingRequest(requestId).map(locked -> {
            int quantity = Math.min(locked.dcStock().availableAmount(), locked.request().outstanding());
            if (quantity == 0) return new Requested(locked.request(), List.of());
            var transfer = transfer(locked.dcStock(), locked.request(), quantity);
            return new Requested(transfer.request(), List.of(transfer));
        });
    }

    @Override
    @Transactional
    public Optional<ReplenishmentRequest> reject(long requestId) {
        return lockDcStockAndPendingRequest(requestId).map(locked -> requests.rejected(locked.request()));
    }

    @Override
    @Transactional
    public Optional<Shipment> receiveShipment(long shipmentId) {
        return shipments.findInTransitForUpdate(shipmentId).map(shipment -> {
            var target = stockTable.findOrCreateForUpdate(shipment.location(), shipment.productName(), shipment.type());
            stockTable.addAvailable(target.id(), shipment.quantity());
            return shipments.arrived(shipment.id()).toDomain();
        });
    }

    @Override
    public List<Shipment> findInTransit(Instant dispatchedBefore) {
        return shipments.findInTransit(dispatchedBefore).stream().map(ShipmentRow::toDomain).toList();
    }

    @Override
    public List<ReplenishmentRequest> findPending() {
        return requests.findPending();
    }

    @Override
    public List<ReplenishmentRequest> findRecent(Replenished location, int limit) {
        return requests.findRecent(location, limit);
    }

    /**
     * Shares the (locked) DC stock among the pending requests of its product, see {@link FairShare}; requests are
     * locked oldest first, their target rows in the same order.
     */
    private List<Transfer> allocate(StockRow dcStock) {
        var pending = requests.findPendingForUpdate(dcStock.name());
        var shares = FairShare.allocate(dcStock.availableAmount(),
            pending.stream().map(ReplenishmentRequest::outstanding).toList());
        var transfers = new ArrayList<Transfer>();
        for (int i = 0; i < pending.size(); i++) {
            if (shares.get(i) == 0) continue;
            transfers.add(transfer(dcStock, pending.get(i), shares.get(i)));
        }
        return transfers;
    }

    private record Locked(StockRow dcStock, ReplenishmentRequest request) {}

    /** The request's product is read first without a lock, to keep the lock order: DC stock, then the request. */
    private Optional<Locked> lockDcStockAndPendingRequest(long requestId) {
        return requests.productNameOf(requestId)
            .flatMap(productName -> stockTable.findByNameForUpdate(Locations.DC, productName))
            .flatMap(dcStock -> requests.findPendingForUpdate(requestId)
                .map(request -> new Locked(dcStock, request)));
    }

    /**
     * Ships {@code quantity} (more than 0, at most what the DC has and the request still needs) to the request's
     * location. Its stock row is created already (locked, in the lock order), so the location shows what is in transit
     * to it even before the first arrival. The DC row is changed relative to its stored value, so {@code dcStock}
     * may be a snapshot from before earlier transfers of the same allocation.
     */
    private Transfer transfer(StockRow dcStock, ReplenishmentRequest request, int quantity) {
        stockTable.addAvailable(dcStock.id(), -quantity);
        stockTable.findOrCreateForUpdate(request.location(), dcStock.name(), dcStock.type());
        var shipped = requests.shipped(request, quantity);
        var shipment = shipments.create(shipped, dcStock.type(), quantity);
        return new Transfer(shipped, shipment.toDomain());
    }
}
