package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.Catalog;
import org.svenehrke.triptychdemo.cross.products.CatalogProduct;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.purchasing.PurchasingHandler;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderOrigin;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderRepositorySPI;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Seeding the DC with the catalog products it neither carries nor has on order, as supplier orders. The supplier stubs deliver right away (lead time 0 in
 * tests). The seed is switched on, but its only trigger here is the admin reset: the period close is off.
 */
@QuarkusTest
@TestProfile(DcSeedFlowTest.DcSeed.class)
class DcSeedFlowTest {

    /** Also stops CashpointStub, which would otherwise sell from the stores. */
    public static class DcSeed implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "inventory.dc-seed.enabled", "true",
                "inventory.dc-seed.quantity", "500",
                "quarkus.scheduler.enabled", "false");
        }
    }

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject InventoryRepositorySPI inventory;
    @Inject SupplierOrderRepositorySPI supplierOrders;
    @Inject PurchasingHandler purchasingHandler;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/locations/dc/products").asString().equals("[]") && auditHelper.isEmpty();
        });
    }

    @Test
    void an_empty_dc_orders_every_catalog_product_and_receives_it() {
        purchasingHandler.seedDc(500);

        assertThat(auditHelper.findEventDetails("PurchasingHandler: DC_SEEDED")).containsExactly("28 products × 500");
        assertThat(auditHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_PLACED")).hasSize(4);
        awaitEveryCatalogProductAtTheDc(500);
    }

    @Test
    void a_product_the_dc_carries_is_not_seeded() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 3);

        purchasingHandler.seedDc(500);

        assertThat(auditHelper.findEventDetails("PurchasingHandler: DC_SEEDED")).containsExactly("27 products × 500");
        awaitEveryCatalogProductAtTheDc(500, Map.of("Apple", 3));
    }

    @Test
    void a_product_on_order_is_not_seeded() {
        var manual = supplierOrders.open("Apple", ProductType.FRUIT, 10, SupplierOrderOrigin.MANUAL); // recorded, never sent

        purchasingHandler.seedDc(500);

        assertThat(auditHelper.findEventDetails("PurchasingHandler: DC_SEEDED")).containsExactly("27 products × 500");
        await().atMost(15, SECONDS).untilAsserted(() ->
            assertThat(supplierOrders.findOpen()).singleElement().satisfies(o -> assertThat(o.id()).isEqualTo(manual.id())));
    }

    /** A supplier that was down: its order was cancelled, so the next seed orders the product again. */
    @Test
    void a_cancelled_order_is_seeded_again() {
        var failed = supplierOrders.open("Apple", ProductType.FRUIT, 500, SupplierOrderOrigin.SEED);
        supplierOrders.cancel(failed.id());

        purchasingHandler.seedDc(500);

        assertThat(auditHelper.findEventDetails("PurchasingHandler: DC_SEEDED")).containsExactly("28 products × 500");
        awaitEveryCatalogProductAtTheDc(500);
    }

    @Test
    void seeding_twice_orders_once() {
        purchasingHandler.seedDc(500);
        awaitEveryCatalogProductAtTheDc(500);

        purchasingHandler.seedDc(500);

        assertThat(auditHelper.findEventDetails("PurchasingHandler: DC_SEEDED")).hasSize(1);
        assertThat(supplierOrders.findOpen()).isEmpty();
    }

    /** Like two pods seeding at once: the advisory lock lets one of them seed, the other then finds its orders. */
    @Test
    void concurrent_seeds_order_once() throws Exception {
        var start = new CountDownLatch(1);
        var seeds = new ArrayList<Future<?>>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                seeds.add(executor.submit(() -> {
                    start.await();
                    purchasingHandler.seedDc(500);
                    return null;
                }));
            }
            start.countDown();
        }
        for (var seed : seeds) seed.get();

        assertThat(auditHelper.findEventDetails("PurchasingHandler: DC_SEEDED")).hasSize(1);
        awaitEveryCatalogProductAtTheDc(500);
    }

    @Test
    void the_admin_reset_seeds_the_dc() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 3);

        given().post("/admin/reset");

        awaitEveryCatalogProductAtTheDc(500);
        // via HTTP: the polling thread has no request context for a direct call
        assertThat(given().get("/admin/page").jsonPath().getList("vm.catalog.name"))
            .containsExactlyElementsOf(Catalog.PRODUCTS.stream().map(CatalogProduct::name).toList());
    }

    private void awaitEveryCatalogProductAtTheDc(int amount) {
        awaitEveryCatalogProductAtTheDc(amount, Map.of());
    }

    /**
     * Exactly {@code amount} of each catalog product (or what {@code others} says) - also proves nothing was ordered
     * twice - and every order delivered.
     */
    private void awaitEveryCatalogProductAtTheDc(int amount, Map<String, Integer> others) {
        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> products = given().get("/api/locations/dc/products").jsonPath().getList("$");
            assertThat(products).hasSize(Catalog.PRODUCTS.size()).allSatisfy(p ->
                assertThat(p.get("availableAmount")).isEqualTo(others.getOrDefault((String) p.get("name"), amount)));
            assertThat(given().get("/admin/supplier-orders-fragment").jsonPath().getList("vm.supplierOrders")).isEmpty();
        });
    }
}
