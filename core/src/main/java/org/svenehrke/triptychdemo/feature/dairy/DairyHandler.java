package org.svenehrke.triptychdemo.feature.dairy;

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
public class DairyHandler {

    @Inject
    DairySupplierSPI dairySupplier;
    @Inject
    SupplierOrderRepositorySPI supplierOrders;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    AsyncEvents inventoryEvents;

    public void order(DairyOrder dairyOrder) {
        auditLog.log("DairyHandler: DAIRY_ORDER_PROCESSING", dairyOrder.productName() + " qty=" + dairyOrder.quantity());
        place(supplierOrders.open(dairyOrder.productName(), ProductType.DAIRY, dairyOrder.quantity(), SupplierOrderOrigin.MANUAL));
    }

    /**
     * Sends a recorded order to the supplier - a manual one, or an automatic one ({@code PurchasingHandler}). If that
     * fails, the order is cancelled, so it no longer counts towards the DC's inventory position.
     */
    public void place(SupplierOrder order) {
        var dairyOrder = new DairyOrder(order.productName(), order.quantity());
        try {
            dairySupplier.placeOrder(dairyOrder);
        } catch (RuntimeException e) {
            supplierOrders.cancel(order.id()).ifPresent(cancelled ->
                auditLog.log("DairyHandler: DAIRY_ORDER_CANCELLED", cancelled.describe() + ": " + e));
            throw e;
        } finally {
            inventoryEvents.fire(new SupplierOrdersChanged());
        }
        auditLog.log("DairyHandler: DAIRY_ORDER_PLACED", dairyOrder.productName() + " qty=" + dairyOrder.quantity());
    }
}
