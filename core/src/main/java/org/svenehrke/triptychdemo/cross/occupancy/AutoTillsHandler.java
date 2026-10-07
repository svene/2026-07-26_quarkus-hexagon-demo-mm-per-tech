package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.location.Store;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens and closes the stores' tills by their occupancy ({@link TillPolicy}), at the checkout systems, which own the
 * tills. After a change a store gets no new decision until a report measured {@link #COOLDOWN} after it, so the
 * change has shown its effect before it is judged again (reports come every 5 s).
 *
 * <p>The last change per store is kept in memory: with two pods only the pod consuming the store's occupancy reports
 * decides for it; after a rebalance the other pod starts without it - at worst one extra step.
 */
@ApplicationScoped
public class AutoTillsHandler {

    static final Duration COOLDOWN = Duration.ofSeconds(10);

    @Inject
    OccupancyRepositorySPI repository;
    @Inject
    CheckoutSystemSPI checkoutSystem;
    @Inject
    AuditLogSPI auditLog;

    private final Map<Store, Instant> lastChange = new ConcurrentHashMap<>();

    /**
     * Judges the latest report of {@code store}. Synchronized: two reports of a store may be judged at once (async
     * events), and both would open a till.
     */
    public synchronized void adjust(Store store) {
        var occupancy = repository.findAll().stream().filter(o -> o.store().equals(store)).findFirst();
        if (occupancy.isEmpty() || inCooldown(occupancy.get())) return;
        TillPolicy.decide(occupancy.get()).ifPresent(tillCount -> change(occupancy.get(), tillCount));
    }

    private boolean inCooldown(StoreOccupancy occupancy) {
        var changedAt = lastChange.get(occupancy.store());
        return changedAt != null && occupancy.measuredAt().isBefore(changedAt.plus(COOLDOWN));
    }

    private void change(StoreOccupancy o, TillCount tillCount) {
        var details = o.store().id() + ": " + o.tills() + "→" + tillCount.tills() + ", queuing " + o.queuing()
            + ", tills busy " + o.tillsBusy() + ", inside " + o.inside() + "/" + o.capacity();
        try {
            checkoutSystem.setTills(tillCount);
        } catch (RuntimeException e) {
            // judged again with the next report
            auditLog.log("AutoTillsHandler: TILLS_CHANGE_FAILED", details + ": " + e.getMessage());
            return;
        }
        lastChange.put(o.store(), Instant.now());
        auditLog.log(tillCount.tills() > o.tills() ? "AutoTillsHandler: TILLS_OPENED" : "AutoTillsHandler: TILLS_CLOSED", details);
    }
}
