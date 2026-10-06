package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import org.svenehrke.triptychdemo.cross.inventory.InventoryReset;
import org.svenehrke.triptychdemo.cross.inventory.LevelsRecalculated;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Seeds the DC with every catalog product it neither carries nor has on order (as supplier orders), so the demo runs
 * on its own after a start or a reset. Shortly after the start ({@code inventory.dc-seed.startup-delay}: once the HTTP
 * server listens, so the supplier stubs in the same app are reachable), right after the admin reset, and at every
 * period close. The repeated check is harmless, and it re-orders what a supplier that was down could not take. Off
 * with {@code inventory.dc-seed.enabled=false} (tests, e2e). Enabled and quantity are looked up per seed, so a test can
 * switch them without its own Quarkus instance.
 */
@ApplicationScoped
public class DcSeedReceiver {

    @ConfigProperty(name = "inventory.dc-seed.enabled")
    Supplier<Boolean> enabled;
    @ConfigProperty(name = "inventory.dc-seed.quantity")
    Supplier<Integer> quantity;
    @ConfigProperty(name = "inventory.dc-seed.startup-delay")
    Duration startupDelay;
    @Inject
    PurchasingHandler purchasingHandler;
    @Inject
    AuditLogHandler auditLog;
    @Inject
    Vertx vertx;

    /** A one-off timer; the seed blocks (JDBC, supplier calls), so it runs on a worker thread, not the event loop. */
    void onStart(@Observes StartupEvent event) {
        if (!enabled.get()) return;
        vertx.setTimer(startupDelay.toMillis(), id -> vertx.executeBlocking(() -> {
            seed();
            return null;
        }));
    }

    void onInventoryReset(@ObservesAsync InventoryReset event) {
        seed();
    }

    void onLevelsRecalculated(@ObservesAsync LevelsRecalculated event) {
        seed();
    }

    /** Nobody waits for an async observer, so a failure is audit-logged (see {@code DeliveryEventReceiver}). */
    private void seed() {
        if (!enabled.get()) return;
        try {
            purchasingHandler.seedDc(quantity.get());
        } catch (RuntimeException e) {
            Log.error("Seeding the DC failed", e);
            auditLog.log("DcSeedReceiver: DC_SEED_FAILED", e.toString());
        }
    }
}
