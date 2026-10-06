package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.purchasing.PurchasingHandler;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderOrigin;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderRepositorySPI;
import org.svenehrke.triptychdemo.cross.reorder.ReorderPolicyHandler;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentHandler;
import org.svenehrke.triptychdemo.cross.replenishment.StockRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Learned DC levels and automatic supplier orders. Automatic replenishment of the stores stays off, so stock only
 * leaves the DC on the requests a test makes; the period is closed by calling {@link ReorderPolicyHandler} directly.
 * The supplier stubs deliver right away (via Kafka): their lead time is 0 in tests.
 * <p>
 * Cold-start DC levels (avg 60, var 60, L 2, R 3): min 136, max 316.
 */
@QuarkusTest
class AutoPurchasingFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject InventoryRepositorySPI inventory;
    @Inject SupplierOrderRepositorySPI supplierOrders;
    @Inject PurchasingHandler purchasingHandler;
    @Inject ReplenishmentHandler replenishmentHandler;
    @Inject ReorderPolicyHandler reorderPolicyHandler;

    @BeforeEach
    void setUp() {
        TestConfigOverrides.set("inventory.auto-purchasing.enabled", "true");
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/locations/dc/products").asString().equals("[]") && auditHelper.isEmpty();
        });
    }

    @AfterEach
    void tearDown() {
        TestConfigOverrides.clear();
    }

    @Test
    void a_request_that_takes_the_dc_below_min_orders_up_to_max_from_the_supplier() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 200);

        replenishmentHandler.request(new StockRequest(Locations.BERN, "Apple", 100));

        // DC 100 < min 136: orders 316 − 100; the stub delivers it, which closes the order
        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(available(Locations.DC, "Apple")).isEqualTo(316);
            // via HTTP: the polling thread has no request context for a direct call
            assertThat(given().get("/admin/supplier-orders-fragment").jsonPath().getList("vm.supplierOrders")).isEmpty();
        });
        assertThat(auditHelper.findEventDetails("PurchasingHandler: AUTO_SUPPLIER_ORDER_CREATED")).singleElement()
            .asString().endsWith("Apple 0/216 OPEN AUTOMATIC");
        assertThat(auditHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_PLACED")).containsExactly("Apple qty=216");
        await().atMost(5, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("InventoryHandler: SUPPLIER_ORDER_DELIVERED")).singleElement()
                .asString().endsWith("Apple 216/216 DELIVERED AUTOMATIC"));
    }

    @Test
    void requests_the_dc_cannot_serve_raise_the_order_quantity() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);

        replenishmentHandler.request(new StockRequest(Locations.BERN, "Apple", 100));

        // position 0 − 100 backordered: orders 316 + 100, then the delivery serves Bern first
        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(available(Locations.BERN, "Apple")).isEqualTo(100);
            assertThat(available(Locations.DC, "Apple")).isEqualTo(316);
        });
        assertThat(auditHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_PLACED")).containsExactly("Apple qty=416");
    }

    @Test
    void an_open_supplier_order_counts_towards_the_position() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        // recorded, never sent: stays open
        supplierOrders.open("Apple", ProductType.FRUIT, 140, SupplierOrderOrigin.MANUAL);

        purchasingHandler.orderIfLow("Apple");

        assertThat(auditHelper.findEventDetails("PurchasingHandler: AUTO_SUPPLIER_ORDER_CREATED")).isEmpty();
        assertThat(purchasingHandler.listOpen()).hasSize(1);
    }

    @Test
    void concurrent_checks_create_one_supplier_order() throws Exception {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);

        var start = new CountDownLatch(1);
        var checks = new ArrayList<Future<?>>();
        try (var executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                checks.add(executor.submit(() -> {
                    start.await();
                    purchasingHandler.orderIfLow("Apple");
                    return null;
                }));
            }
            start.countDown();
        }
        for (var check : checks) check.get();

        assertThat(auditHelper.findEventDetails("PurchasingHandler: AUTO_SUPPLIER_ORDER_CREATED")).hasSize(1);
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(available(Locations.DC, "Apple")).isEqualTo(316));
    }

    @Test
    void the_period_close_learns_the_dc_levels_from_the_requests() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 1000);
        replenishmentHandler.request(new StockRequest(Locations.BERN, "Apple", 30));
        replenishmentHandler.request(new StockRequest(Locations.ZURICH, "Apple", 50));

        reorderPolicyHandler.closePeriod();

        // demand 80, e = 20: avg 66, var 0.7·(60 + 0.3·400) = 126; min ⌈132 + 2·√126⌉ = 155, max ⌈155 + 198⌉ = 353
        var dc = given().get("/admin/inventory-fragment").jsonPath()
            .setRootPath("vm.products.find { it.name == 'Apple' }.levels[0]");
        assertThat(dc.getInt("min")).isEqualTo(155);
        assertThat(dc.getInt("max")).isEqualTo(353);
        assertThat(auditHelper.findEventDetails("ReorderPolicyHandler: PERIOD_CLOSED")).containsExactly("5 rows");
        // 920 left: above min, nothing ordered
        await().during(1, SECONDS).atMost(2, SECONDS).until(() ->
            auditHelper.findEventDetails("PurchasingHandler: AUTO_SUPPLIER_ORDER_CREATED").isEmpty());
    }

    private static int available(Location location, String productName) {
        Integer amount = given().get("/api/locations/" + location.id() + "/products").jsonPath()
            .get("find { it.name == '" + productName + "' }.availableAmount");
        return amount == null ? 0 : amount;
    }
}
