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
    public Optional<Transfer> request(StockRequest request) {
        var dcStock = StockEntity.findByNameForUpdate(Locations.DC, request.productName()).orElse(null);
        if (dcStock == null) return Optional.empty();
        boolean olderPending = ReplenishmentRequestEntity.anyPending(request.productName());
        var entity = ReplenishmentRequestEntity.create(request);
        int quantity = olderPending ? 0 : serve(dcStock, entity);
        return Optional.of(new Transfer(entity.toDomain(), quantity));
    }

    @Override
    @Transactional
    public List<Transfer> fulfilPending(String productName) {
        var dcStock = StockEntity.findByNameForUpdate(Locations.DC, productName).orElse(null);
        if (dcStock == null) return List.of();
        var transfers = new ArrayList<Transfer>();
        for (var request : ReplenishmentRequestEntity.findPendingForUpdate(productName)) {
            if (dcStock.availableAmount == 0) break;
            int quantity = serve(dcStock, request);
            transfers.add(new Transfer(request.toDomain(), quantity));
        }
        return transfers;
    }

    @Override
    @Transactional
    public Optional<Transfer> fulfil(long requestId) {
        return lockDcStockAndPendingRequest(requestId).map(locked -> {
            int quantity = serve(locked.dcStock(), locked.request());
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

    private record Locked(StockEntity dcStock, ReplenishmentRequestEntity request) {}

    /** The request's product is read first without a lock, to keep the lock order: DC stock, then the request. */
    private static Optional<Locked> lockDcStockAndPendingRequest(long requestId) {
        return ReplenishmentRequestEntity.productNameOf(requestId)
            .flatMap(productName -> StockEntity.findByNameForUpdate(Locations.DC, productName))
            .flatMap(dcStock -> ReplenishmentRequestEntity.findPendingForUpdate(requestId)
                .map(request -> new Locked(dcStock, request)));
    }

    /** Moves as much as the DC has, at most what is outstanding, to the request's location. */
    private static int serve(StockEntity dcStock, ReplenishmentRequestEntity request) {
        int quantity = Math.min(dcStock.availableAmount, request.outstanding());
        if (quantity == 0) return 0;
        dcStock.availableAmount -= quantity;
        var location = Locations.replenishedOf(request.locationId);
        var target = StockEntity.findForUpdate(location, dcStock.name, dcStock.type)
            .orElseGet(() -> StockEntity.create(location, dcStock.name, dcStock.type));
        target.availableAmount += quantity;
        request.delivered += quantity;
        if (request.outstanding() == 0) request.status = RequestStatus.FULFILLED;
        return quantity;
    }
}
