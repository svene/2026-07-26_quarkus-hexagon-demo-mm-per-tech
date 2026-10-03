package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.ReplenishmentChanged;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Pull replenishment: stores and the online FC request stock from the DC. The serving rules (oldest first,
 * partial) and their transactions live in {@link ReplenishmentRepositorySPI}.
 * <p>
 * Every change fires a {@link ReplenishmentChanged}, also when no stock moved, since the pages showing stock show the
 * requests, too.
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
        var transfer = replenishmentRepository.request(request);
        if (transfer.isEmpty()) {
            auditLog.log("ReplenishmentHandler: REQUEST_REJECTED",
                request.location().id() + ": " + request.productName() + ": not carried by the DC");
            return Optional.empty();
        }
        logTransfer(transfer.get());
        inventoryEvents.fireAsync(new ReplenishmentChanged(request.location()));
        return Optional.of(transfer.get().request());
    }

    /** After a delivery to the DC: serves what was waiting for it. */
    public void fulfilPending(String productName) {
        var transfers = replenishmentRepository.fulfilPending(productName);
        transfers.forEach(this::logTransfer);
        transfers.stream().map(t -> t.request().location()).distinct()
            .forEach(location -> inventoryEvents.fireAsync(new ReplenishmentChanged(location)));
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
