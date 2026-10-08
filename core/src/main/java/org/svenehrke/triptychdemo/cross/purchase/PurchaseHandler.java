package org.svenehrke.triptychdemo.cross.purchase;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.inventory.OnShortage;
import org.svenehrke.triptychdemo.cross.inventory.Shortage;
import org.svenehrke.triptychdemo.cross.inventory.StockDeducted;
import org.svenehrke.triptychdemo.cross.inventory.StockDeduction;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.location.Store;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.events.AsyncEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Online checkout and physical-store sale differ only in what a shortage means - see {@link OnShortage}. Both record
 * what the customer asked for as demand, which the reorder levels are learned from ({@code ReorderPolicyHandler}).
 * A completed purchase is also kept for {@link #RETENTION}, for the Purchases table on {@code /locations}.
 */
@ApplicationScoped
public class PurchaseHandler {

    /** How long a completed purchase is kept: far more than the latest purchases a page shows. */
    static final Duration RETENTION = Duration.ofMinutes(30);

    @Inject
    InventoryRepositorySPI inventoryRepository;
    @Inject
    PurchaseRepositorySPI purchaseRepository;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    AsyncEvents inventoryEvents;

    /**
     * Online purchase (shop, JSON API), from the online FC's stock: all-or-nothing, rejected if any item is not
     * in stock - also when several customers buy concurrently.
     */
    public PurchaseOutcome checkout(Purchase purchase) {
        var deduction = deduct(Locations.ONLINE, purchase, OnShortage.REJECT);
        if (!deduction.shortages().isEmpty()) {
            auditLog.log("PurchaseHandler: PURCHASE_REJECTED", Locations.ONLINE.id() + ": " +
                deduction.shortages().stream().map(Shortage::message).collect(Collectors.joining(", ")));
            return new PurchaseOutcome.Rejected(deduction.shortages());
        }
        return new PurchaseOutcome.Completed();
    }

    /**
     * Physical-store sale (cashpoint): the goods are already gone, so it is recorded, never rejected. Selling
     * more than is on record means the inventory was wrong; that is audit-logged as a stock discrepancy.
     */
    public void recordStoreSale(Store store, Purchase purchase) {
        var deduction = deduct(store, purchase, OnShortage.CAP_AT_ZERO);
        deduction.shortages().forEach(shortage ->
            auditLog.log("PurchaseHandler: STOCK_DISCREPANCY", store.id() + ": " + shortage.discrepancyMessage()));
    }

    /** The most recent completed purchases of {@code location}, newest first. */
    public List<RecordedPurchase> listRecent(Replenished location, int limit) {
        return purchaseRepository.findRecent(location, limit);
    }

    /**
     * Records the demand after the deduction, so a product the location had no row for is reported as a shortage only.
     * A completed purchase is stored before {@link StockDeducted} is fired, so the pages re-fetching on it show it.
     */
    private StockDeduction deduct(Replenished location, Purchase purchase, OnShortage onShortage) {
        auditLog.log("PurchaseHandler: PURCHASE_PROCESSING", location.id() + ": " +
            purchase.items().stream().map(i -> i.productName() + " qty=" + i.quantity()).collect(Collectors.joining(", ")));
        var quantitiesByName = quantitiesByName(purchase);
        var deduction = inventoryRepository.deductAll(location, quantitiesByName, onShortage);
        inventoryRepository.recordDemand(location, quantitiesByName);
        boolean completed = onShortage == OnShortage.CAP_AT_ZERO || deduction.shortages().isEmpty();
        if (completed) {
            var now = Instant.now();
            purchaseRepository.append(location, purchase, now, now.minus(RETENTION));
        }
        if (!deduction.updated().isEmpty()) {
            inventoryEvents.fire(new StockDeducted(location,
                deduction.updated().stream().map(Product::name).collect(Collectors.toSet())));
            auditLog.log("PurchaseHandler: INVENTORY_DEDUCTED", location.id() + ": " + deduction.updated().stream()
                .map(p -> p.name() + " -" + quantitiesByName.get(p.name()) + " total=" + p.availableAmount())
                .collect(Collectors.joining(", ")));
        }
        return deduction;
    }

    /**
     * Sums repeated products, so each is checked against its total; sorted by name, so concurrent
     * purchases always lock the same products in the same order and cannot deadlock.
     */
    private static SortedMap<String, Integer> quantitiesByName(Purchase purchase) {
        return purchase.items().stream()
            .collect(Collectors.toMap(PurchaseItem::productName, PurchaseItem::quantity, Integer::sum, TreeMap::new));
    }
}
