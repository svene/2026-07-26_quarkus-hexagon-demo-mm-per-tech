package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.feature.bakery.BakeryHandler;
import org.svenehrke.triptychdemo.feature.beverage.BeveragesHandler;
import org.svenehrke.triptychdemo.feature.dairy.DairyHandler;
import org.svenehrke.triptychdemo.feature.fruit.FruitsHandler;
import org.svenehrke.triptychdemo.feature.meat.MeatHandler;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodHandler;
import org.svenehrke.triptychdemo.feature.vegetable.VegetablesHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Central purchasing: the DC orders from the suppliers by itself when it runs low. An automatic order is recorded here
 * and then sent by the commodity Handler of its product type, exactly like a manual one (validation, audit, supplier).
 */
@ApplicationScoped
public class PurchasingHandler {

    @Inject
    SupplierOrderRepositorySPI supplierOrders;
    @Inject
    AuditLogSPI auditLog;
    @Inject
    FruitsHandler fruitsHandler;
    @Inject
    VegetablesHandler vegetablesHandler;
    @Inject
    DairyHandler dairyHandler;
    @Inject
    BeveragesHandler beveragesHandler;
    @Inject
    MeatHandler meatHandler;
    @Inject
    BakeryHandler bakeryHandler;
    @Inject
    NonFoodHandler nonFoodHandler;

    /**
     * Orders {@code productName} from its supplier if the DC's inventory position has fallen below its learned reorder
     * point (see {@link SupplierOrderRepositorySPI#openIfLow}).
     */
    public void orderIfLow(String productName) {
        supplierOrders.openIfLow(productName).ifPresent(order -> {
            auditLog.log("PurchasingHandler: AUTO_SUPPLIER_ORDER_CREATED", order.describe());
            place(order);
        });
    }

    public List<SupplierOrder> listOpen() {
        return supplierOrders.findOpen();
    }

    private void place(SupplierOrder order) {
        switch (order.type()) {
            case FRUIT -> fruitsHandler.place(order);
            case VEGETABLE -> vegetablesHandler.place(order);
            case DAIRY -> dairyHandler.place(order);
            case BEVERAGE -> beveragesHandler.place(order);
            case MEAT -> meatHandler.place(order);
            case BAKERY -> bakeryHandler.place(order);
            case NON_FOOD -> nonFoodHandler.place(order);
        }
    }
}
