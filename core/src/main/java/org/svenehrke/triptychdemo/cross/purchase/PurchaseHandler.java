package org.svenehrke.triptychdemo.cross.purchase;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.inventory.OnShortage;
import org.svenehrke.triptychdemo.cross.inventory.Shortage;
import org.svenehrke.triptychdemo.cross.inventory.StockDeducted;
import org.svenehrke.triptychdemo.cross.inventory.StockDeduction;
import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Store;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Online checkout and physical-store sale differ only in what a shortage means - see {@link OnShortage}. */
@ApplicationScoped
public class PurchaseHandler {

    @Inject
    InventoryRepositorySPI inventoryRepository;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    Event<InventoryEvent> inventoryEvents;

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

    private StockDeduction deduct(Location location, Purchase purchase, OnShortage onShortage) {
        auditLog.log("PurchaseHandler: PURCHASE_PROCESSING", location.id() + ": " +
            purchase.items().stream().map(i -> i.productName() + " qty=" + i.quantity()).collect(Collectors.joining(", ")));
        var quantitiesByName = quantitiesByName(purchase);
        var deduction = inventoryRepository.deductAll(location, quantitiesByName, onShortage);
        if (!deduction.updated().isEmpty()) {
            inventoryEvents.fireAsync(new StockDeducted(location));
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
