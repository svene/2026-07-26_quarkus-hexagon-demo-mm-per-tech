package org.svenehrke.triptychdemo.feature.nonfood;

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
public class NonFoodHandler {

    @Inject
    NonFoodSupplierSPI nonFoodSupplier;
    @Inject
    SupplierOrderRepositorySPI supplierOrders;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    Event<InventoryEvent> inventoryEvents;

    public void order(NonFoodOrder nonFoodOrder) {
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_PROCESSING", nonFoodOrder.productName() + " qty=" + nonFoodOrder.quantity());
        place(supplierOrders.open(nonFoodOrder.productName(), ProductType.NON_FOOD, nonFoodOrder.quantity(), SupplierOrderOrigin.MANUAL));
    }

    /**
     * Sends a recorded order to the supplier - a manual one, or an automatic one ({@code PurchasingHandler}). If that
     * fails, the order is cancelled, so it no longer counts towards the DC's inventory position.
     */
    public void place(SupplierOrder order) {
        var nonFoodOrder = new NonFoodOrder(order.productName(), order.quantity());
        try {
            nonFoodSupplier.placeOrder(nonFoodOrder);
        } catch (RuntimeException e) {
            supplierOrders.cancel(order.id()).ifPresent(cancelled ->
                auditLog.log("NonFoodHandler: NONFOOD_ORDER_CANCELLED", cancelled.describe() + ": " + e));
            throw e;
        } finally {
            inventoryEvents.fireAsync(new SupplierOrdersChanged());
        }
        auditLog.log("NonFoodHandler: NONFOOD_ORDER_PLACED", nonFoodOrder.productName() + " qty=" + nonFoodOrder.quantity());
    }
}
