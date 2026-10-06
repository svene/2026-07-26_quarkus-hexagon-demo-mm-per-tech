package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.inventory.StockTable;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.CatalogProduct;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentRequestTable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every method locks the product's DC stock row first, then its supplier orders - the DC row is what serializes them
 * with the requests of {@code ReplenishmentService}, which lock it first, too. Except {@link #openSeed}: a product the
 * DC doesn't carry has no row to lock.
 */
@ApplicationScoped
public class SupplierOrderService implements SupplierOrderRepositorySPI {

    /** Advisory lock key of {@link #openSeed}: any constant no other code uses. */
    private static final long SEED_LOCK = 4_711_001L;

    @Inject
    StockTable stockTable;

    @Inject
    SupplierOrderTable orders;

    @Inject
    ReplenishmentRequestTable requests;

    @Override
    @Transactional
    public SupplierOrder open(String productName, ProductType type, int quantity, SupplierOrderOrigin origin) {
        stockTable.findForUpdate(Locations.DC, productName, type);
        return orders.create(productName, type, quantity, origin);
    }

    /**
     * With the DC row locked, no request and no supplier order of the product can be stored or served concurrently, so
     * the position stays valid until the new order is stored.
     */
    @Override
    @Transactional
    public Optional<SupplierOrder> openIfLow(String productName) {
        var dcStock = stockTable.findByNameForUpdate(Locations.DC, productName).orElse(null);
        if (dcStock == null) return Optional.empty();
        int outstanding = orders.outstanding(productName) - requests.outstanding(productName);
        int quantity = dcStock.levels().reorderQuantity(dcStock.availableAmount(), outstanding);
        if (quantity <= 0) return Optional.empty();
        return Optional.of(orders.create(productName, dcStock.type(),
            Math.min(quantity, SupplierOrder.MAX_QUANTITY), SupplierOrderOrigin.AUTOMATIC));
    }

    /**
     * A product the DC doesn't carry has no stock row to lock, so an advisory lock serializes concurrent seeds (two
     * pods, or a period close and a reset at the same time): the second one waits, then finds the first one's orders.
     * The open order is checked before the stock: a delivery turns "no stock, order open" into "stock, order delivered"
     * in one transaction, but the two checks are separate statements, so one can commit in between. In this order,
     * either check sees it; the other way round, both can miss it and the product is ordered again.
     */
    @Override
    @Transactional
    public List<SupplierOrder> openSeed(List<CatalogProduct> products, int quantity) {
        orders.advisoryLock(SEED_LOCK);
        return products.stream()
            .filter(p -> orders.outstanding(p.name()) == 0 && stockTable.findType(Locations.DC, p.name()).isEmpty())
            .map(p -> orders.create(p.name(), p.type(), quantity, SupplierOrderOrigin.SEED))
            .toList();
    }

    /** The order's product is read first without a lock, to keep the lock order: DC stock, then the order. */
    @Override
    @Transactional
    public Optional<SupplierOrder> cancel(long id) {
        return orders.productNameOf(id).flatMap(productName -> {
            stockTable.findByNameForUpdate(Locations.DC, productName);
            return orders.findOpenForUpdate(id);
        }).map(orders::cancelled);
    }

    @Override
    @Transactional
    public List<SupplierOrder> receiveDelivery(String productName, ProductType type, int quantity) {
        var dcStock = stockTable.findOrCreateForUpdate(Locations.DC, productName, type);
        stockTable.addAvailable(dcStock.id(), quantity);
        var served = new ArrayList<SupplierOrder>();
        int rest = quantity;
        for (var order : orders.findOpenForUpdate(productName)) {
            if (rest == 0) break;
            int share = Math.min(rest, order.outstanding());
            rest -= share;
            served.add(orders.delivered(order, share));
        }
        return served;
    }

    @Override
    public List<SupplierOrder> findOpen() {
        return orders.findOpen();
    }
}
