package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryHandler;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.purchasing.PurchasingHandler;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrder;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderOrigin;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderRepositorySPI;
import org.svenehrke.triptychdemo.feature.fruit.FruitDelivery;
import org.svenehrke.triptychdemo.feature.fruit.FruitOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitSupplierSPI;
import org.svenehrke.triptychdemo.feature.fruit.FruitsHandler;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Recording supplier orders and closing them by delivery. The fruit supplier is mocked, so nothing is delivered unless
 * a test delivers it ({@link InventoryHandler}, as the Kafka receiver would) - an order stays open until then.
 */
@QuarkusTest
class SupplierOrderFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject SupplierOrderRepositorySPI supplierOrders;
    @Inject PurchasingHandler purchasingHandler;
    @Inject FruitsHandler fruitsHandler;
    @Inject InventoryHandler inventoryHandler;
    @InjectMock FruitSupplierSPI fruitSupplier;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/locations/dc/products").asString().equals("[]") && auditHelper.isEmpty();
        });
    }

    @Test
    void a_manual_order_is_recorded_and_shown_until_delivered() {
        fruitsHandler.order(new FruitOrder("Apple", 10));

        var supplierOrder = given().get("/admin/supplier-orders-fragment").jsonPath().setRootPath("vm.supplierOrders[0]");
        assertThat(supplierOrder.getString("productName")).isEqualTo("Apple");
        assertThat(supplierOrder.getString("type")).isEqualTo("FRUIT");
        assertThat(supplierOrder.getInt("quantity")).isEqualTo(10);
        assertThat(supplierOrder.getString("origin")).isEqualTo("MANUAL");

        inventoryHandler.updateFruitAmount(new FruitDelivery("Apple", 10));

        assertThat(given().get("/admin/supplier-orders-fragment").jsonPath().getList("vm.supplierOrders")).isEmpty();
        assertThat(auditHelper.findEventDetails("InventoryHandler: SUPPLIER_ORDER_DELIVERED")).singleElement()
            .asString().endsWith("Apple 10/10 DELIVERED MANUAL");
    }

    @Test
    void a_delivery_closes_open_orders_oldest_first() {
        var older = supplierOrders.open("Apple", ProductType.FRUIT, 5, SupplierOrderOrigin.MANUAL);
        var newer = supplierOrders.open("Apple", ProductType.FRUIT, 5, SupplierOrderOrigin.AUTOMATIC);

        inventoryHandler.updateFruitAmount(new FruitDelivery("Apple", 7));

        assertThat(purchasingHandler.listOpen()).singleElement().satisfies(open -> {
            assertThat(open.id()).isEqualTo(newer.id());
            assertThat(open.delivered()).isEqualTo(2);
        });
        assertThat(purchasingHandler.listOpen()).extracting(SupplierOrder::id).doesNotContain(older.id());
        assertThat(dcAvailable("Apple")).isEqualTo(7);
    }

    @Test
    void a_delivery_beyond_the_open_orders_is_simply_added() {
        supplierOrders.open("Apple", ProductType.FRUIT, 5, SupplierOrderOrigin.MANUAL);

        inventoryHandler.updateFruitAmount(new FruitDelivery("Apple", 8));
        inventoryHandler.updateFruitAmount(new FruitDelivery("Pear", 3));

        assertThat(purchasingHandler.listOpen()).isEmpty();
        assertThat(dcAvailable("Apple")).isEqualTo(8);
        assertThat(dcAvailable("Pear")).isEqualTo(3);
        assertThat(auditHelper.findEventDetails("InventoryHandler: SUPPLIER_ORDER_DELIVERED")).hasSize(1);
    }

    @Test
    void an_order_the_supplier_does_not_accept_is_cancelled() {
        doThrow(new IllegalStateException("supplier down")).when(fruitSupplier).placeOrder(any());

        assertThatIllegalStateException().isThrownBy(() -> fruitsHandler.order(new FruitOrder("Apple", 10)));

        assertThat(purchasingHandler.listOpen()).isEmpty();
        assertThat(auditHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_CANCELLED")).singleElement()
            .asString().contains("Apple 0/10 CANCELLED MANUAL");
        assertThat(auditHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_PLACED")).isEmpty();
    }

    private static int dcAvailable(String productName) {
        Integer amount = given().get("/api/locations/" + Locations.DC.id() + "/products").jsonPath()
            .get("find { it.name == '" + productName + "' }.availableAmount");
        return amount == null ? 0 : amount;
    }
}
