package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryHandler;
import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentService;
import org.svenehrke.triptychdemo.feature.fruit.FruitDelivery;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
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
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;

/**
 * Stores and the online FC pull stock from the DC: {@code /locations/{id}/requests}, {@code /admin} decisions. What the
 * DC ships reaches the location through the carrier stub (Kafka, 0 s transit time in tests), so a location's stock is
 * awaited.
 */
@QuarkusTest
class ReplenishmentFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject InventoryRepositorySPI inventory;
    @Inject InventoryHandler inventoryHandler;
    @InjectSpy ReplenishmentService replenishmentService;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/locations/dc/products").asString().equals("[]") && auditHelper.isEmpty();
        });
    }

    @Test
    void supplier_deliveries_go_to_the_dc_only() {
        given().contentType(ContentType.JSON)
            .body("""
                {"productName": "Mango", "quantity": 5}
                """)
            .post("/api/products/order-fruits").then().statusCode(204);

        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(available(Locations.DC, "Mango")).isEqualTo(5));
        for (var location : List.of(Locations.ZURICH, Locations.BERN, Locations.BASEL, Locations.ONLINE)) {
            assertThat(given().get("/api/locations/" + location.id() + "/products").asString()).isEqualTo("[]");
        }
    }

    @Test
    void request_the_dc_can_serve_is_transferred_right_away() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);

        request(Locations.BERN, "Apple", "4").then().statusCode(200);

        assertThat(available(Locations.DC, "Apple")).isEqualTo(6);
        awaitAvailable(Locations.BERN, "Apple", 4);
        var requests = locationInventory(Locations.BERN);
        assertThat(requests.getList("requests.status")).containsExactly("FULFILLED");
        assertThat(requests.getInt("requests[0].shipped")).isEqualTo(4);
        assertThat(auditHelper.findEventDetails("LocationReceiver: REQUEST_RECEIVED")).containsExactly("bern: Apple qty=4");
        assertThat(auditHelper.findEventDetails("ReplenishmentHandler: STOCK_SHIPPED")).singleElement().asString()
            .matches("shipment \\d+ dc → bern: Apple 4 \\(request .*\\)");
        // logged right after the arrival committed
        await().atMost(5, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("ReplenishmentHandler: SHIPMENT_ARRIVED")).singleElement().asString()
                .matches("shipment \\d+ dc → bern: Apple 4"));
    }

    @Test
    void request_beyond_dc_stock_takes_what_is_there_and_waits_for_the_next_delivery() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 3);

        request(Locations.ONLINE, "Apple", "5").then().statusCode(200);

        assertThat(available(Locations.DC, "Apple")).isZero();
        awaitAvailable(Locations.ONLINE, "Apple", 3);
        assertThat(given().get("/admin/requests-fragment").jsonPath().getList("vm.requests.shipped")).containsExactly(3);

        inventoryHandler.updateFruitAmount(new FruitDelivery("Apple", 10));

        // the pending request is served by DeliveryEventReceiver, asynchronously
        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(available(Locations.ONLINE, "Apple")).isEqualTo(5));
        assertThat(available(Locations.DC, "Apple")).isEqualTo(8);
        assertThat(given().get("/admin/requests-fragment").jsonPath().getList("vm.requests")).isEmpty();
    }

    @Test
    void a_short_delivery_is_shared_fairly_among_the_pending_requests() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        request(Locations.BERN, "Apple", "5").then().statusCode(200);
        request(Locations.ZURICH, "Apple", "5").then().statusCode(200);

        inventoryHandler.updateFruitAmount(new FruitDelivery("Apple", 7));

        // 3.5 each: the unit left over goes to the older request
        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(available(Locations.ZURICH, "Apple")).isEqualTo(3));
        awaitAvailable(Locations.BERN, "Apple", 4);
        assertThat(available(Locations.DC, "Apple")).isZero();
        var pending = given().get("/admin/requests-fragment").jsonPath();
        assertThat(pending.getList("vm.requests.locationName")).containsExactly("Store Bern", "Store Zurich");
        assertThat(pending.getList("vm.requests.shipped")).containsExactly(4, 3);
    }

    @Test
    void a_failure_serving_the_pending_requests_neither_fails_nor_repeats_the_delivery() {
        inventory.addAmount(Locations.DC, "Mango", ProductType.FRUIT, 0);
        request(Locations.BERN, "Mango", "3").then().statusCode(200);
        doThrow(new IllegalStateException("database hiccup")).when(replenishmentService).allocate("Mango");

        given().contentType(ContentType.JSON)
            .body("""
                {"productName": "Mango", "quantity": 5}
                """)
            .post("/api/products/order-fruits").then().statusCode(204);

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("DeliveryEventReceiver: FULFIL_PENDING_FAILED"))
                .containsExactly("Mango: java.lang.IllegalStateException: database hiccup"));
        // counted once: the Kafka receiver did not retry the delivery
        assertThat(auditHelper.findEventDetails("InventoryHandler: FRUIT_INVENTORY_UPDATED")).containsExactly("dc: Mango +5");
        assertThat(available(Locations.DC, "Mango")).isEqualTo(5);
        // the request waits for the next delivery (or head office)
        assertThat(given().get("/admin/requests-fragment").jsonPath().getList("vm.requests.productName")).containsExactly("Mango");
    }

    @Test
    void a_new_request_shares_the_dc_stock_with_older_pending_ones() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        request(Locations.BERN, "Apple", "5").then().statusCode(200);
        // stock that reached the DC without a delivery (e.g. a stock correction) - the next allocation shares it
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 5);

        request(Locations.ZURICH, "Apple", "2").then().statusCode(200);

        // 5·5/7 = 3.57, 5·2/7 = 1.43 → 3 and 1, the unit left over to the larger remainder
        awaitAvailable(Locations.BERN, "Apple", 4);
        awaitAvailable(Locations.ZURICH, "Apple", 1);
        assertThat(given().get("/admin/requests-fragment").jsonPath().getList("vm.requests.locationName"))
            .containsExactly("Store Bern", "Store Zurich");
    }

    @Test
    void head_office_fulfils_a_request_partially_ahead_of_older_ones_and_rejects_the_rest() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        request(Locations.BERN, "Apple", "5").then().statusCode(200);
        request(Locations.BASEL, "Apple", "6").then().statusCode(200);
        long basel = given().get("/admin/requests-fragment").jsonPath().getLong("vm.requests[1].id");
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 4);

        var fulfil = given().post("/admin/requests/" + basel + "/fulfil");
        assertThat(fulfil.statusCode()).isEqualTo(200);
        assertThat(fulfil.asString()).isEmpty();
        awaitAvailable(Locations.BASEL, "Apple", 4);

        given().post("/admin/requests/" + basel + "/reject").then().statusCode(200);

        assertThat(given().get("/admin/requests-fragment").jsonPath().getList("vm.requests.locationName"))
            .containsExactly("Store Bern");
        var baselRequests = locationInventory(Locations.BASEL);
        assertThat(baselRequests.getList("requests.status")).containsExactly("REJECTED");
        assertThat(baselRequests.getInt("requests[0].shipped")).isEqualTo(4);
        // stock already shipped stays shipped
        assertThat(available(Locations.BASEL, "Apple")).isEqualTo(4);
    }

    @Test
    void deciding_on_a_request_that_is_no_longer_pending_returns_409() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);
        request(Locations.BERN, "Apple", "4").then().statusCode(200);
        long id = locationInventory(Locations.BERN).getLong("requests[0].id");

        for (var action : List.of("fulfil", "reject")) {
            var response = given().post("/admin/requests/" + id + "/" + action);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.jsonPath().getList("vm.messages")).containsExactly("request " + id + " is no longer pending");
        }
        given().post("/admin/requests/999999/fulfil").then().statusCode(409);
    }

    @Test
    void request_for_a_product_the_dc_never_carried_returns_409() {
        var response = request(Locations.BERN, "Ghost", "1");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.jsonPath().getList("vm.messages")).containsExactly("Ghost: not carried by the DC");
        assertThat(locationInventory(Locations.BERN).getList("requests")).isEmpty();
    }

    @Test
    void invalid_request_returns_400() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);

        var response = request(Locations.BERN, "Apple", "2001");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getList("vm.messages")).containsExactly("must be less than or equal to 2000");
        assertThat(available(Locations.DC, "Apple")).isEqualTo(10);
    }

    @Test
    void sales_only_touch_their_own_locations_stock() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.BERN, "Apple", ProductType.FRUIT, 10);

        given().contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":3}]}
                """)
            .post("/api/products/purchase").then().statusCode(204);

        assertThat(available(Locations.ONLINE, "Apple")).isEqualTo(7);
        assertThat(available(Locations.BERN, "Apple")).isEqualTo(10);
    }

    @Test
    void concurrent_requests_never_take_more_than_the_dc_has() throws Exception {
        int stock = 5;
        int requests = 20;
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, stock);

        var start = new CountDownLatch(1);
        var statusCodes = new ArrayList<Future<Integer>>();
        try (var executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                var location = List.of(Locations.ZURICH, Locations.BERN, Locations.BASEL, Locations.ONLINE).get(i % 4);
                statusCodes.add(executor.submit(() -> {
                    start.await();
                    return request(location, "Apple", "1").statusCode();
                }));
            }
            start.countDown();
        }

        for (var statusCode : statusCodes) assertThat(statusCode.get()).isEqualTo(200);
        assertThat(available(Locations.DC, "Apple")).isZero();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            int arrived = 0;
            for (var location : List.of(Locations.ZURICH, Locations.BERN, Locations.BASEL, Locations.ONLINE)) {
                arrived += available(location, "Apple");
            }
            assertThat(arrived).isEqualTo(stock);
        });
        assertThat(given().get("/admin/requests-fragment").jsonPath().getList("vm.requests")).hasSize(requests - stock);
    }

    @Test
    void locations_page_lists_every_dc_product_per_location() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.DC, "Milk", ProductType.DAIRY, 6);
        request(Locations.BERN, "Apple", "4").then().statusCode(200);
        awaitAvailable(Locations.BERN, "Apple", 4);

        var json = given().get("/locations/page").jsonPath();

        assertThat(json.getString("route")).isEqualTo("LocationsPage");
        assertThat(json.getList("vm.locations.locationId")).containsExactly("zurich", "bern", "basel", "online");
        assertThat(json.getList("vm.locations.locationName")).containsExactly("Store Zurich", "Store Bern", "Store Basel", "Online FC");
        json.setRootPath("vm.locations.find { it.locationId == 'bern' }");
        assertThat(json.getList("products.name")).containsExactly("Apple", "Milk");
        assertThat(json.getList("products.availableAmount")).containsExactly(4, 0);
        assertThat(json.getList("products.inTransit")).containsExactly(0, 0);
    }

    @Test
    void locations_shell_loads_the_page_of_all_locations_without_a_nav() {
        var response = given().get("/locations");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("<title>Supermarket – Locations</title>")
            .contains("hx-get=\"/locations/page\"")
            .doesNotContain("location-nav")
            .doesNotContain("{{");
    }

    @Test
    void the_dc_and_unknown_locations_have_no_location_endpoints() {
        given().get("/locations/dc/inventory-fragment").then().statusCode(404);
        given().get("/locations/paris/inventory-fragment").then().statusCode(404);
        given().get("/locations/bern").then().statusCode(404);
        given().get("/api/locations/paris/products").then().statusCode(404);
    }

    private static io.restassured.response.Response request(Location location, String productName, String quantity) {
        return given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", productName)
            .formParam("quantity", quantity)
            .post("/locations/" + location.id() + "/requests");
    }

    private static JsonPath locationInventory(Location location) {
        return given().get("/locations/" + location.id() + "/inventory-fragment").jsonPath().setRootPath("vm");
    }

    private static void awaitAvailable(Location location, String productName, int expected) {
        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(available(location, productName)).isEqualTo(expected));
    }

    private static int available(Location location, String productName) {
        Integer amount = given().get("/api/locations/" + location.id() + "/products").jsonPath()
            .get("find { it.name == '" + productName + "' }.availableAmount");
        return amount == null ? 0 : amount;
    }
}
