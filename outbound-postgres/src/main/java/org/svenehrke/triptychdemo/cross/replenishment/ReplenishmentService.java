package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.inventory.StockEntity;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.location.Locations;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every method that touches a product's requests locks that product's DC stock row first, so all of them are
 * serialized per product; then the requests, then the target location's row (see the SPI for why).
 */
@ApplicationScoped
public class ReplenishmentService implements ReplenishmentRepositorySPI {

    @Override
    @Transactional
    public Optional<Requested> request(StockRequest request) {
        var dcStock = StockEntity.findByNameForUpdate(Locations.DC, request.productName()).orElse(null);
        if (dcStock == null) return Optional.empty();
        var entity = ReplenishmentRequestEntity.create(request, RequestOrigin.MANUAL);
        var transfers = allocate(dcStock);
        return Optional.of(new Requested(entity.toDomain(), transfers));
    }

    /**
     * The location's row is locked after the DC row (the lock order of {@link #transfer}); with the DC row locked, no
     * other request of the product can be stored concurrently, so the outstanding sum stays valid until the new
     * request is stored.
     */
    @Override
    @Transactional
    public Optional<ReplenishmentRequest> requestIfLow(Replenished location, String productName) {
        var dcStock = StockEntity.findByNameForUpdate(Locations.DC, productName).orElse(null);
        if (dcStock == null) return Optional.empty();
        var stock = StockEntity.findForUpdate(location, productName, dcStock.type)
            .orElseGet(() -> StockEntity.create(location, productName, dcStock.type));
        int quantity = stock.levels().reorderQuantity(stock.availableAmount,
            ReplenishmentRequestEntity.outstanding(location.id(), productName));
        if (quantity <= 0) return Optional.empty();
        var request = new StockRequest(location, productName, Math.min(quantity, StockRequest.MAX_QUANTITY));
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
    public Optional<Transfer> fulfil(long requestId) {
        return lockDcStockAndPendingRequest(requestId).map(locked -> {
            int quantity = Math.min(locked.dcStock().availableAmount, locked.request().outstanding());
            transfer(locked.dcStock(), locked.request(), quantity);
            return new Transfer(locked.request().toDomain(), quantity);
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
            transfer(dcStock, pending.get(i), shares.get(i));
            transfers.add(new Transfer(pending.get(i).toDomain(), shares.get(i)));
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

    /** Moves {@code quantity} (at most what the DC has and the request still needs) to the request's location. */
    private static void transfer(StockEntity dcStock, ReplenishmentRequestEntity request, int quantity) {
        if (quantity == 0) return;
        dcStock.availableAmount -= quantity;
        var location = Locations.replenishedOf(request.locationId);
        var target = StockEntity.findForUpdate(location, dcStock.name, dcStock.type)
            .orElseGet(() -> StockEntity.create(location, dcStock.name, dcStock.type));
        target.availableAmount += quantity;
        request.delivered += quantity;
        if (request.outstanding() == 0) request.status = RequestStatus.FULFILLED;
    }
}
