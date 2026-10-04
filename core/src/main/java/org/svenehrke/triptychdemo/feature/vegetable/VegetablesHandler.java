package org.svenehrke.triptychdemo.feature.vegetable;

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
public class VegetablesHandler {

    @Inject
    VegetablesSupplierSPI vegetablesSupplier;
    @Inject
    SupplierOrderRepositorySPI supplierOrders;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    Event<InventoryEvent> inventoryEvents;

    public void order(VegetableOrder vegetableOrder) {
        auditLog.log("VegetablesHandler: VEGETABLES_ORDER_PROCESSING", vegetableOrder.productName() + " qty=" + vegetableOrder.quantity());
        place(supplierOrders.open(vegetableOrder.productName(), ProductType.VEGETABLE, vegetableOrder.quantity(), SupplierOrderOrigin.MANUAL));
    }

    /**
     * Sends a recorded order to the supplier - a manual one, or an automatic one ({@code PurchasingHandler}). If that
     * fails, the order is cancelled, so it no longer counts towards the DC's inventory position.
     */
    public void place(SupplierOrder order) {
        var vegetableOrder = new VegetableOrder(order.productName(), order.quantity());
        try {
            vegetablesSupplier.placeOrder(vegetableOrder);
        } catch (RuntimeException e) {
            supplierOrders.cancel(order.id()).ifPresent(cancelled ->
                auditLog.log("VegetablesHandler: VEGETABLES_ORDER_CANCELLED", cancelled.describe() + ": " + e));
            throw e;
        } finally {
            inventoryEvents.fireAsync(new SupplierOrdersChanged());
        }
        auditLog.log("VegetablesHandler: VEGETABLES_ORDER_PLACED", vegetableOrder.productName() + " qty=" + vegetableOrder.quantity());
    }
}
