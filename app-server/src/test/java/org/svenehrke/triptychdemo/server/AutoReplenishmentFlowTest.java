package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Store;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseHandler;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
import org.svenehrke.triptychdemo.cross.reorder.ReorderPolicyHandler;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

/**
 * Learned reorder levels and automatic replenishment of the stores and the online FC. The period is closed by calling
 * {@link ReorderPolicyHandler} directly, so the tests are deterministic; the timer stays off.
 */
@QuarkusTest
class AutoReplenishmentFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject InventoryRepositorySPI inventory;
    @Inject PurchaseHandler purchaseHandler;
    @Inject ReorderPolicyHandler reorderPolicyHandler;

    @BeforeEach
    void setUp() {
        TestConfigOverrides.set("inventory.auto-replenishment.enabled", "true");
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
    void demand_includes_lost_online_sales_but_not_products_the_dc_never_carried() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);

        checkout("Apple", 5).then().statusCode(409);
        checkout("Ghost", 1).then().statusCode(409);
        reorderPolicyHandler.closePeriod();

        // initial avg 15, α 0.3: 15 + 0.3·(5 − 15)
        assertThat(row(Locations.ONLINE, "Apple").getDouble("avgDemand")).isCloseTo(12, within(1e-9));
        assertThat(given().get("/api/locations/online/products").jsonPath().getList("name")).containsExactly("Apple");
        assertThat(auditHelper.findEventDetails("ReorderPolicyHandler: PERIOD_CLOSED")).containsExactly("5 rows");
    }

    @Test
    void the_period_close_learns_the_levels_and_starts_a_new_period() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        // capped at 0: the goods are gone anyway, and the demand counts in full
        sell(Locations.BERN, "Apple", 20);

        reorderPolicyHandler.closePeriod();

        // initial (10, 10), e = 10: avg 13, var 0.7·(10 + 0.3·100) = 28; min ⌈13 + 2·√28⌉, max ⌈24 + 13·3⌉
        var bern = row(Locations.BERN, "Apple");
        assertThat(bern.getDouble("avgDemand")).isCloseTo(13, within(1e-9));
        assertThat(bern.getInt("levels.min")).isEqualTo(24);
        assertThat(bern.getInt("levels.max")).isEqualTo(63);

        reorderPolicyHandler.closePeriod();

        // the period demand was reset: 13 + 0.3·(0 − 13), not 13 + 0.3·(20 − 13)
        assertThat(row(Locations.BERN, "Apple").getDouble("avgDemand")).isCloseTo(9.1, within(1e-9));
    }

    @Test
    void a_sale_below_min_requests_up_to_max_from_the_dc() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 1000);
        // cold-start levels at a store: min 17, max 47
        inventory.addAmount(Locations.BERN, "Apple", ProductType.FRUIT, 20);

        sell(Locations.BERN, "Apple", 5);

        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(available(Locations.BERN, "Apple")).isEqualTo(47));
        var requests = requests(Locations.BERN);
        assertThat(requests.getList("origin")).containsExactly("AUTOMATIC");
        assertThat(requests.getList("requested")).containsExactly(32);
        assertThat(requests.getList("status")).containsExactly("FULFILLED");
        assertThat(available(Locations.DC, "Apple")).isEqualTo(968);
    }

    @Test
    void a_sale_that_stays_at_or_above_min_requests_nothing() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 1000);
        inventory.addAmount(Locations.BERN, "Apple", ProductType.FRUIT, 20);

        sell(Locations.BERN, "Apple", 3);

        await().during(1, SECONDS).atMost(2, SECONDS).until(() -> requests(Locations.BERN).getList("id").isEmpty());
        assertThat(available(Locations.BERN, "Apple")).isEqualTo(17);
    }

    @Test
    void further_sales_do_not_order_again_while_a_request_is_outstanding() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        inventory.addAmount(Locations.BERN, "Apple", ProductType.FRUIT, 20);

        sell(Locations.BERN, "Apple", 5);
        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(requests(Locations.BERN).getList("status")).containsExactly("PENDING"));
        // available 13 + outstanding 32 = 45: above min
        sell(Locations.BERN, "Apple", 2);

        await().during(1, SECONDS).atMost(2, SECONDS).until(() -> requests(Locations.BERN).getList("id").size() == 1);
        assertThat(requests(Locations.BERN).getList("requested")).containsExactly(32);
    }

    @Test
    void concurrent_sales_create_one_request() throws Exception {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        inventory.addAmount(Locations.BERN, "Apple", ProductType.FRUIT, 20);

        var start = new CountDownLatch(1);
        var sales = new ArrayList<Future<?>>();
        try (var executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                sales.add(executor.submit(() -> {
                    start.await();
                    sell(Locations.BERN, "Apple", 1);
                    return null;
                }));
            }
            start.countDown();
        }
        for (var sale : sales) sale.get();

        await().atMost(5, SECONDS).until(() -> requests(Locations.BERN).getList("id").size() == 1);
        await().during(1, SECONDS).atMost(2, SECONDS).until(() -> requests(Locations.BERN).getList("id").size() == 1);
        assertThat(available(Locations.BERN, "Apple")).isEqualTo(10);
    }

    @Test
    void the_period_close_fills_empty_stores_and_the_online_fc_from_the_dc() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 1000);

        reorderPolicyHandler.closePeriod();

        // no demand: store avg 7, var 28 → min 18, max 39; online avg 10.5, var 57.75 → min 26, max 58
        await().atMost(5, SECONDS).untilAsserted(() -> {
            for (var store : List.of(Locations.ZURICH, Locations.BERN, Locations.BASEL)) {
                assertThat(available(store, "Apple")).isEqualTo(39);
            }
            assertThat(available(Locations.ONLINE, "Apple")).isEqualTo(58);
        });
        assertThat(available(Locations.DC, "Apple")).isEqualTo(1000 - 3 * 39 - 58);
        assertThat(requests(Locations.ONLINE).getList("origin")).containsExactly("AUTOMATIC");
    }

    @Test
    void the_period_close_shares_too_little_dc_stock_among_all_locations() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 50);

        reorderPolicyHandler.closePeriod();

        // requests 39, 39, 39, 58 (Σ 175) against 50 in the DC
        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(available(Locations.ONLINE, "Apple")).isEqualTo(17));
        for (var store : List.of(Locations.ZURICH, Locations.BERN, Locations.BASEL)) {
            assertThat(available(store, "Apple")).isEqualTo(11);
        }
        assertThat(available(Locations.DC, "Apple")).isZero();
        assertThat(given().get("/admin/requests-fragment").jsonPath().getList("vm.requests.status"))
            .containsOnly("PENDING").hasSize(4);
    }

    private void sell(Store store, String productName, int quantity) {
        purchaseHandler.recordStoreSale(store, new Purchase(List.of(new PurchaseItem(productName, quantity))));
    }

    private static io.restassured.response.Response checkout(String productName, int quantity) {
        return given().contentType(ContentType.JSON)
            .body("{\"items\":[{\"productName\":\"" + productName + "\",\"quantity\":" + quantity + "}]}")
            .post("/api/products/purchase");
    }

    private static JsonPath row(Location location, String productName) {
        return given().get("/locations/" + location.id() + "/inventory-fragment").jsonPath()
            .setRootPath("vm.products.find { it.name == '" + productName + "' }");
    }

    private static JsonPath requests(Location location) {
        return given().get("/locations/" + location.id() + "/inventory-fragment").jsonPath().setRootPath("vm.requests");
    }

    private static int available(Location location, String productName) {
        Integer amount = given().get("/api/locations/" + location.id() + "/products").jsonPath()
            .get("find { it.name == '" + productName + "' }.availableAmount");
        return amount == null ? 0 : amount;
    }
}
