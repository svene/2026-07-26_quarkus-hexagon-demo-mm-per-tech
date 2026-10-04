package org.svenehrke.triptychdemo.feature.beverage;

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
public class BeveragesHandler {

    @Inject
    BeverageSupplierSPI beverageSupplier;
    @Inject
    SupplierOrderRepositorySPI supplierOrders;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    Event<InventoryEvent> inventoryEvents;

    public void order(BeverageOrder beverageOrder) {
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_PROCESSING", beverageOrder.productName() + " qty=" + beverageOrder.quantity());
        place(supplierOrders.open(beverageOrder.productName(), ProductType.BEVERAGE, beverageOrder.quantity(), SupplierOrderOrigin.MANUAL));
    }

    /**
     * Sends a recorded order to the supplier - a manual one, or an automatic one ({@code PurchasingHandler}). If that
     * fails, the order is cancelled, so it no longer counts towards the DC's inventory position.
     */
    public void place(SupplierOrder order) {
        var beverageOrder = new BeverageOrder(order.productName(), order.quantity());
        try {
            beverageSupplier.placeOrder(beverageOrder);
        } catch (RuntimeException e) {
            supplierOrders.cancel(order.id()).ifPresent(cancelled ->
                auditLog.log("BeveragesHandler: BEVERAGES_ORDER_CANCELLED", cancelled.describe() + ": " + e));
            throw e;
        } finally {
            inventoryEvents.fireAsync(new SupplierOrdersChanged());
        }
        auditLog.log("BeveragesHandler: BEVERAGES_ORDER_PLACED", beverageOrder.productName() + " qty=" + beverageOrder.quantity());
    }
}
