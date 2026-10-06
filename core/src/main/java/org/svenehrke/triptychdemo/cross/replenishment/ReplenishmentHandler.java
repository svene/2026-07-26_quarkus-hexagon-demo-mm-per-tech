package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.DcDemandChanged;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.ReplenishmentChanged;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.events.AsyncEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
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
 * <p>
 * Every transfer ships: its {@link Shipment} is handed to the {@link CarrierSPI} after the transaction that recorded it
 * has committed, and the location books it when the carrier reports the arrival ({@link #receiveShipment}). A shipment
 * whose dispatch got lost is sent again by {@link #redispatchOverdue}.
 */
@ApplicationScoped
public class ReplenishmentHandler {

    @Inject
    ReplenishmentRepositorySPI replenishmentRepository;
    @Inject
    CarrierSPI carrier;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    AsyncEvents inventoryEvents;

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
        ship(transfers);
        if (stored.status() == RequestStatus.PENDING && transfers.stream().noneMatch(t -> t.request().id() == stored.id())) {
            auditLog.log("ReplenishmentHandler: REQUEST_PENDING", describe(stored));
        }
        fireChanged(Stream.concat(Stream.of(stored.location()), locations(transfers)));
        inventoryEvents.fire(new DcDemandChanged(request.productName()));
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
        ship(transfers);
        fireChanged(locations(transfers));
    }

    /** Head office. Empty if there is no such pending request; else its state afterwards. */
    public Optional<ReplenishmentRequest> fulfil(long requestId) {
        auditLog.log("ReplenishmentHandler: FULFIL_PROCESSING", "request " + requestId);
        var fulfilled = replenishmentRepository.fulfil(requestId);
        fulfilled.ifPresent(f -> {
            ship(f.transfers());
            if (f.transfers().isEmpty()) logPending(f.request());
            inventoryEvents.fire(new ReplenishmentChanged(f.request().location()));
        });
        return fulfilled.map(Requested::request);
    }

    /** Head office. Empty if there is no such pending request. */
    public Optional<ReplenishmentRequest> reject(long requestId) {
        auditLog.log("ReplenishmentHandler: REJECT_PROCESSING", "request " + requestId);
        var rejected = replenishmentRepository.reject(requestId);
        rejected.ifPresent(r -> {
            auditLog.log("ReplenishmentHandler: REQUEST_CANCELLED", describe(r));
            inventoryEvents.fire(new ReplenishmentChanged(r.location()));
        });
        return rejected;
    }

    /** The carrier reports an arrival. A repeated or unknown one changes nothing. */
    public void receiveShipment(long shipmentId) {
        auditLog.log("ReplenishmentHandler: SHIPMENT_ARRIVAL_PROCESSING", "shipment " + shipmentId);
        replenishmentRepository.receiveShipment(shipmentId).ifPresentOrElse(
            shipment -> {
                auditLog.log("ReplenishmentHandler: SHIPMENT_ARRIVED", describe(shipment));
                inventoryEvents.fire(new ReplenishmentChanged(shipment.location()));
            },
            () -> auditLog.log("ReplenishmentHandler: SHIPMENT_ARRIVAL_IGNORED",
                "shipment " + shipmentId + ": not in transit (unknown or arrived already)"));
    }

    /**
     * Sends every shipment that is in transit for longer than {@code overdueAfter} to the carrier again: its dispatch
     * may have been lost (a crash between the commit and the send), or the carrier may have lost it. Harmless if it
     * was not, since an arrival is booked only once.
     */
    public void redispatchOverdue(Duration overdueAfter) {
        replenishmentRepository.findInTransit(Instant.now().minus(overdueAfter)).forEach(shipment -> {
            auditLog.log("ReplenishmentHandler: SHIPMENT_REDISPATCHED", describe(shipment));
            dispatch(shipment);
        });
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
        ship(transfers);
        created.stream()
            .filter(r -> transfers.stream().noneMatch(t -> t.request().id() == r.id()))
            .forEach(r -> auditLog.log("ReplenishmentHandler: REQUEST_PENDING", describe(r)));
        fireChanged(Stream.concat(created.stream().map(ReplenishmentRequest::location), locations(transfers)));
        inventoryEvents.fire(new DcDemandChanged(productName));
    }

    private static Stream<Replenished> locations(List<Transfer> transfers) {
        return transfers.stream().map(t -> t.request().location());
    }

    private void fireChanged(Stream<Replenished> locations) {
        locations.distinct().forEach(location -> inventoryEvents.fire(new ReplenishmentChanged(location)));
    }

    private void ship(List<Transfer> transfers) {
        transfers.forEach(transfer -> {
            auditLog.log("ReplenishmentHandler: STOCK_SHIPPED", describe(transfer.shipment()) + " (" + describe(transfer.request()) + ")");
            logPending(transfer.request());
            dispatch(transfer.shipment());
        });
    }

    /**
     * The shipment is committed already, so a failing carrier must not fail the operation that shipped it: the
     * failure is audit-logged, and {@link #redispatchOverdue} sends it again.
     */
    private void dispatch(Shipment shipment) {
        try {
            carrier.dispatch(shipment);
        } catch (RuntimeException e) {
            auditLog.log("ReplenishmentHandler: SHIPMENT_DISPATCH_FAILED", describe(shipment) + ": " + e);
        }
    }

    private void logPending(ReplenishmentRequest r) {
        if (r.status() == RequestStatus.PENDING) {
            auditLog.log("ReplenishmentHandler: REQUEST_PENDING", describe(r));
        }
    }

    private static String describe(Shipment s) {
        return "shipment " + s.id() + " dc → " + s.location().id() + ": " + s.productName() + " " + s.quantity();
    }

    private static String describe(ReplenishmentRequest r) {
        return "request " + r.id() + " " + r.location().id() + ": " + r.productName()
            + " " + r.shipped() + "/" + r.requested() + " " + r.status();
    }
}
