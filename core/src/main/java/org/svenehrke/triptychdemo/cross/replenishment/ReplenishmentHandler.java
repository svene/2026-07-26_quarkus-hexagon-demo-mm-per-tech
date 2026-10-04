package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.DcDemandChanged;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.ReplenishmentChanged;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Pull replenishment: stores and the online FC request stock from the DC. How the DC stock is shared
 * ({@link FairShare}) and the transactions live in {@link ReplenishmentRepositorySPI}.
 * <p>
 * Every change fires a {@link ReplenishmentChanged}, also when no stock moved, since the pages showing stock show the
 * requests, too. Creating a request also fires a {@link DcDemandChanged}: it lowers the DC's inventory position, as a
 * new backorder or as stock leaving the DC. Serving a request that already exists does not, since its backorder
 * shrinks by what leaves.
 */
@ApplicationScoped
public class ReplenishmentHandler {

    @Inject
    ReplenishmentRepositorySPI replenishmentRepository;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    Event<InventoryEvent> inventoryEvents;

    /** Empty if the DC has never carried the product. */
    public Optional<ReplenishmentRequest> request(StockRequest request) {
        auditLog.log("ReplenishmentHandler: REQUEST_PROCESSING",
            request.location().id() + ": " + request.productName() + " qty=" + request.quantity());
        var requested = replenishmentRepository.request(request);
        if (requested.isEmpty()) {
            auditLog.log("ReplenishmentHandler: REQUEST_REJECTED",
                request.location().id() + ": " + request.productName() + ": not carried by the DC");
            return Optional.empty();
        }
        var stored = requested.get().request();
        var transfers = requested.get().transfers();
        transfers.forEach(this::logTransfer);
        if (stored.status() == RequestStatus.PENDING && transfers.stream().noneMatch(t -> t.request().id() == stored.id())) {
            auditLog.log("ReplenishmentHandler: REQUEST_PENDING", describe(stored));
        }
        fireChanged(Stream.concat(Stream.of(stored.location()), locations(transfers)));
        inventoryEvents.fireAsync(new DcDemandChanged(request.productName()));
        return Optional.of(stored);
    }

    /**
     * Automatic replenishment after a sale: requests from the DC whichever of {@code productNames} has fallen below its
     * learned reorder point at {@code location} (see {@link ReplenishmentRepositorySPI#requestIfLow}).
     */
    public void replenishIfLow(Replenished location, Collection<String> productNames) {
        productNames.forEach(productName -> replenishIfLow(List.of(location), productName));
    }

    /**
     * Automatic replenishment after the levels changed: for each product, first collects the requests of every store
     * and the online FC, then shares the DC stock among them in one go - so a shortfall hits all of them alike (see
     * {@link FairShare}).
     */
    public void replenishAllIfLow(Collection<String> productNames) {
        productNames.forEach(productName -> replenishIfLow(Locations.REPLENISHED, productName));
    }

    /** After a delivery to the DC: shares it among the requests waiting for it. */
    public void fulfilPending(String productName) {
        var transfers = replenishmentRepository.allocate(productName);
        transfers.forEach(this::logTransfer);
        fireChanged(locations(transfers));
    }

    /** Head office. Empty if there is no such pending request. */
    public Optional<Transfer> fulfil(long requestId) {
        auditLog.log("ReplenishmentHandler: FULFIL_PROCESSING", "request " + requestId);
        var transfer = replenishmentRepository.fulfil(requestId);
        transfer.ifPresent(t -> {
            logTransfer(t);
            inventoryEvents.fireAsync(new ReplenishmentChanged(t.request().location()));
        });
        return transfer;
    }

    /** Head office. Empty if there is no such pending request. */
    public Optional<ReplenishmentRequest> reject(long requestId) {
        auditLog.log("ReplenishmentHandler: REJECT_PROCESSING", "request " + requestId);
        var rejected = replenishmentRepository.reject(requestId);
        rejected.ifPresent(r -> {
            auditLog.log("ReplenishmentHandler: REQUEST_CANCELLED", describe(r));
            inventoryEvents.fireAsync(new ReplenishmentChanged(r.location()));
        });
        return rejected;
    }

    public List<ReplenishmentRequest> listPending() {
        return replenishmentRepository.findPending();
    }

    public List<ReplenishmentRequest> listRecent(Replenished location, int limit) {
        return replenishmentRepository.findRecent(location, limit);
    }

    private void replenishIfLow(List<? extends Replenished> locations, String productName) {
        var created = locations.stream()
            .flatMap(location -> replenishmentRepository.requestIfLow(location, productName).stream())
            .toList();
        if (created.isEmpty()) return;
        created.forEach(r -> auditLog.log("ReplenishmentHandler: AUTO_REQUEST_CREATED", describe(r)));
        var transfers = replenishmentRepository.allocate(productName);
        transfers.forEach(this::logTransfer);
        created.stream()
            .filter(r -> transfers.stream().noneMatch(t -> t.request().id() == r.id()))
            .forEach(r -> auditLog.log("ReplenishmentHandler: REQUEST_PENDING", describe(r)));
        fireChanged(Stream.concat(created.stream().map(ReplenishmentRequest::location), locations(transfers)));
        inventoryEvents.fireAsync(new DcDemandChanged(productName));
    }

    private static Stream<Replenished> locations(List<Transfer> transfers) {
        return transfers.stream().map(t -> t.request().location());
    }

    private void fireChanged(Stream<Replenished> locations) {
        locations.distinct().forEach(location -> inventoryEvents.fireAsync(new ReplenishmentChanged(location)));
    }

    private void logTransfer(Transfer transfer) {
        var r = transfer.request();
        if (transfer.quantity() > 0) {
            auditLog.log("ReplenishmentHandler: STOCK_TRANSFERRED",
                "dc → " + r.location().id() + ": " + r.productName() + " " + transfer.quantity() + " (" + describe(r) + ")");
        }
        if (r.status() == RequestStatus.PENDING) {
            auditLog.log("ReplenishmentHandler: REQUEST_PENDING", describe(r));
        }
    }

    private static String describe(ReplenishmentRequest r) {
        return "request " + r.id() + " " + r.location().id() + ": " + r.productName()
            + " " + r.delivered() + "/" + r.requested() + " " + r.status();
    }
}
