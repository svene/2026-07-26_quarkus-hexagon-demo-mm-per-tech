package org.svenehrke.triptychdemo.feature.fruit;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.inventory.InventoryEvent;
import org.svenehrke.triptychdemo.cross.inventory.SupplierOrdersChanged;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrder;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderOrigin;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderRepositorySPI;
import org.svenehrke.triptychdemo.cross.events.AsyncEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FruitsHandler {

    @Inject
    FruitSupplierSPI fruitSupplier;
    @Inject
    SupplierOrderRepositorySPI supplierOrders;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    AsyncEvents inventoryEvents;

    public void order(FruitOrder fruitOrder) {
        auditLog.log("FruitsHandler: FRUITS_ORDER_PROCESSING", fruitOrder.productName() + " qty=" + fruitOrder.quantity());
        place(supplierOrders.open(fruitOrder.productName(), ProductType.FRUIT, fruitOrder.quantity(), SupplierOrderOrigin.MANUAL));
    }

    /**
     * Sends a recorded order to the supplier - a manual one, or an automatic one ({@code PurchasingHandler}). If that
     * fails, the order is cancelled, so it no longer counts towards the DC's inventory position.
     */
    public void place(SupplierOrder order) {
        var fruitOrder = new FruitOrder(order.productName(), order.quantity());
        try {
            fruitSupplier.placeOrder(fruitOrder);
        } catch (RuntimeException e) {
            supplierOrders.cancel(order.id()).ifPresent(cancelled ->
                auditLog.log("FruitsHandler: FRUITS_ORDER_CANCELLED", cancelled.describe() + ": " + e));
            throw e;
        } finally {
            inventoryEvents.fire(new SupplierOrdersChanged());
        }
        auditLog.log("FruitsHandler: FRUITS_ORDER_PLACED", fruitOrder.productName() + " qty=" + fruitOrder.quantity());
    }
}
