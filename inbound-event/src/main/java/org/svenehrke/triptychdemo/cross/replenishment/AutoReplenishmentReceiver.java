package org.svenehrke.triptychdemo.cross.replenishment;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.inventory.LevelsRecalculated;
import org.svenehrke.triptychdemo.cross.inventory.StockDeducted;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Replenished;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collection;

/**
 * Automatic replenishment of the stores and the online FC: whenever their stock or their reorder levels change, they
 * re-check it against the levels and request from the DC what has fallen below them. Off with
 * {@code inventory.auto-replenishment.enabled=false} (tests, e2e), so stock then only moves on manual requests.
 */
@ApplicationScoped
public class AutoReplenishmentReceiver {

    @ConfigProperty(name = "inventory.auto-replenishment.enabled")
    boolean enabled;
    @Inject
    ReplenishmentHandler replenishmentHandler;
    @Inject
    ProductsHandler productsHandler;
    @Inject
    AuditLogHandler auditLog;

    void onStockDeducted(@ObservesAsync StockDeducted event) {
        if (enabled) replenishIfLow(event.location(), event.productNames());
    }

    /**
     * Also what fills a store from empty: the cold-start levels already have {@code min > 0}. All locations at once, so
     * the DC shares a shortfall among them.
     */
    void onLevelsRecalculated(@ObservesAsync LevelsRecalculated event) {
        if (!enabled) return;
        var dcProducts = productsHandler.listAll(Locations.DC).stream().map(Product::name).toList();
        try {
            replenishmentHandler.replenishAllIfLow(dcProducts);
        } catch (RuntimeException e) {
            Log.error("Automatic replenishment of all locations failed", e);
            auditLog.log("AutoReplenishmentReceiver: AUTO_REPLENISHMENT_FAILED", "all locations: " + e);
        }
    }

    /**
     * Nobody waits for an async observer, so a failure is audit-logged (see {@code DeliveryEventReceiver}). Nothing is
     * lost: the next sale or period close checks again.
     */
    private void replenishIfLow(Replenished location, Collection<String> productNames) {
        try {
            replenishmentHandler.replenishIfLow(location, productNames);
        } catch (RuntimeException e) {
            Log.errorf(e, "Automatic replenishment of %s failed", location.id());
            auditLog.log("AutoReplenishmentReceiver: AUTO_REPLENISHMENT_FAILED", location.id() + ": " + e);
        }
    }
}
