package org.svenehrke.triptychdemo.feature.bakery;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.SupplierOrdersChanged;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrder;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderOrigin;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderRepositorySPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class BakeryHandler {

    @Inject
    BakerySupplierSPI bakerySupplier;
    @Inject
    SupplierOrderRepositorySPI supplierOrders;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    Event<InventoryEvent> inventoryEvents;

    public void order(BakeryOrder bakeryOrder) {
        auditLog.log("BakeryHandler: BAKERY_ORDER_PROCESSING", bakeryOrder.productName() + " qty=" + bakeryOrder.quantity());
        place(supplierOrders.open(bakeryOrder.productName(), ProductType.BAKERY, bakeryOrder.quantity(), SupplierOrderOrigin.MANUAL));
    }

    /**
     * Sends a recorded order to the supplier - a manual one, or an automatic one ({@code PurchasingHandler}). If that
     * fails, the order is cancelled, so it no longer counts towards the DC's inventory position.
     */
    public void place(SupplierOrder order) {
        var bakeryOrder = new BakeryOrder(order.productName(), order.quantity());
        try {
            bakerySupplier.placeOrder(bakeryOrder);
        } catch (RuntimeException e) {
            supplierOrders.cancel(order.id()).ifPresent(cancelled ->
                auditLog.log("BakeryHandler: BAKERY_ORDER_CANCELLED", cancelled.describe() + ": " + e));
            throw e;
        } finally {
            inventoryEvents.fireAsync(new SupplierOrdersChanged());
        }
        auditLog.log("BakeryHandler: BAKERY_ORDER_PLACED", bakeryOrder.productName() + " qty=" + bakeryOrder.quantity());
    }
}
