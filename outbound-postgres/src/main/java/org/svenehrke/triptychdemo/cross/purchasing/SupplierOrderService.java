package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.inventory.StockEntity;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentRequestEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every method locks the product's DC stock row first, then its supplier orders - the DC row is what serializes them
 * with the requests of {@code ReplenishmentService}, which lock it first, too.
 */
@ApplicationScoped
public class SupplierOrderService implements SupplierOrderRepositorySPI {

    @Override
    @Transactional
    public SupplierOrder open(String productName, ProductType type, int quantity, SupplierOrderOrigin origin) {
        StockEntity.findForUpdate(Locations.DC, productName, type);
        return SupplierOrderEntity.create(productName, type, quantity, origin).toDomain();
    }

    /**
     * With the DC row locked, no request and no supplier order of the product can be stored or served concurrently, so
     * the position stays valid until the new order is stored.
     */
    @Override
    @Transactional
    public Optional<SupplierOrder> openIfLow(String productName) {
        var dcStock = StockEntity.findByNameForUpdate(Locations.DC, productName).orElse(null);
        if (dcStock == null) return Optional.empty();
        int outstanding = SupplierOrderEntity.outstanding(productName) - ReplenishmentRequestEntity.outstanding(productName);
        int quantity = dcStock.levels().reorderQuantity(dcStock.availableAmount, outstanding);
        if (quantity <= 0) return Optional.empty();
        return Optional.of(SupplierOrderEntity.create(productName, dcStock.type,
            Math.min(quantity, SupplierOrder.MAX_QUANTITY), SupplierOrderOrigin.AUTOMATIC).toDomain());
    }

    /** The order's product is read first without a lock, to keep the lock order: DC stock, then the order. */
    @Override
    @Transactional
    public Optional<SupplierOrder> cancel(long id) {
        return SupplierOrderEntity.productNameOf(id).flatMap(productName -> {
            StockEntity.findByNameForUpdate(Locations.DC, productName);
            return SupplierOrderEntity.findOpenForUpdate(id);
        }).map(order -> {
            order.status = SupplierOrderStatus.CANCELLED;
            return order.toDomain();
        });
    }

    @Override
    @Transactional
    public List<SupplierOrder> receiveDelivery(String productName, ProductType type, int quantity) {
        var dcStock = StockEntity.findForUpdate(Locations.DC, productName, type)
            .orElseGet(() -> StockEntity.create(Locations.DC, productName, type));
        dcStock.availableAmount += quantity;
        var served = new ArrayList<SupplierOrder>();
        int rest = quantity;
        for (var order : SupplierOrderEntity.findOpenForUpdate(productName)) {
            if (rest == 0) break;
            int share = Math.min(rest, order.outstanding());
            order.delivered += share;
            if (order.outstanding() == 0) order.status = SupplierOrderStatus.DELIVERED;
            rest -= share;
            served.add(order.toDomain());
        }
        return served;
    }

    @Override
    public List<SupplierOrder> findOpen() {
        return SupplierOrderEntity.<SupplierOrderEntity>list("status", Sort.by("id"), SupplierOrderStatus.OPEN)
            .stream().map(SupplierOrderEntity::toDomain).toList();
    }
}
