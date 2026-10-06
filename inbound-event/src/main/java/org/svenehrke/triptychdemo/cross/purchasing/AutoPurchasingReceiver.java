package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.inventory.DcDemandChanged;
import org.svenehrke.triptychdemo.cross.inventory.LevelsRecalculated;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.Product;
import org.svenehrke.triptychdemo.cross.products.ProductsHandler;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

/**
 * Automatic supplier orders of the DC: whenever its inventory position or its reorder levels change, it re-checks the
 * position against the levels and orders what has fallen below them. Off with
 * {@code inventory.auto-purchasing.enabled=false} (tests, e2e), so supplier orders are then only placed by hand. Looked
 * up per event, so a test can switch it on without its own Quarkus instance.
 */
@ApplicationScoped
public class AutoPurchasingReceiver {

    @ConfigProperty(name = "inventory.auto-purchasing.enabled")
    Supplier<Boolean> enabled;
    @Inject
    PurchasingHandler purchasingHandler;
    @Inject
    ProductsHandler productsHandler;
    @Inject
    AuditLogHandler auditLog;

    void onDcDemandChanged(@ObservesAsync DcDemandChanged event) {
        if (enabled.get()) orderIfLow(event.productName());
    }

    /** New DC levels take effect even if no store or the online FC requests anything. */
    void onLevelsRecalculated(@ObservesAsync LevelsRecalculated event) {
        if (!enabled.get()) return;
        productsHandler.listAll(Locations.DC).stream().map(Product::name).forEach(this::orderIfLow);
    }

    /**
     * One product at a time, so a supplier that is down does not keep the others from being ordered. Nobody waits for
     * an async observer, so a failure is audit-logged (see {@code DeliveryEventReceiver}). Nothing is lost: the next
     * request or period close checks again.
     */
    private void orderIfLow(String productName) {
        try {
            purchasingHandler.orderIfLow(productName);
        } catch (RuntimeException e) {
            Log.errorf(e, "Automatic supplier order of %s failed", productName);
            auditLog.log("AutoPurchasingReceiver: AUTO_PURCHASING_FAILED", productName + ": " + e);
        }
    }
}
