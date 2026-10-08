package org.svenehrke.triptychdemo.cross.purchase;

import org.svenehrke.triptychdemo.cross.location.Replenished;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class PurchaseService implements PurchaseRepositorySPI {

    @Inject
    PurchaseTable table;

    @Override
    @Transactional
    public void append(Replenished location, Purchase purchase, Instant purchasedAt, Instant keepSince) {
        table.insert(location, purchase.products(), purchase.units(), purchasedAt);
        table.deleteBefore(location, keepSince);
    }

    @Override
    public List<RecordedPurchase> findRecent(Replenished location, int limit) {
        return table.findRecent(location, limit);
    }
}
