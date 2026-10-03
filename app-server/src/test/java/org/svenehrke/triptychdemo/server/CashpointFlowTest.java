package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
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

@QuarkusTest
class CashpointFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/products").asString().equals("[]")
                && auditHelper.isEmpty();
        });
    }

    @Test
    void customer_purchase_deducts_inventory() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);


        auditHelper.clearAuditLog();

        var purchaseResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":3}]}
                """)
            .post("/api/products/purchase");
        assertThat(purchaseResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("ProductApiReceiver: PURCHASE_RECEIVED"))
            .containsExactly("Apple qty=3");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_PROCESSING"))
            .containsExactly("online: Apple qty=3");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
            .containsExactly("online: Apple -3 total=7");

        assertThat(given().get("/api/products").asString())
            .isEqualTo("""
                [{"name":"Apple","type":"FRUIT","availableAmount":7}]""");
    }

    @Test
    void multi_item_purchase_deducts_each_product() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Milk", ProductType.DAIRY, 6);


        auditHelper.clearAuditLog();

        var purchaseResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":3},{"productName":"Milk","quantity":2}]}
                """)
            .post("/api/products/purchase");
        assertThat(purchaseResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_PROCESSING"))
            .containsExactly("online: Apple qty=3, Milk qty=2");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
            .containsExactly("online: Apple -3 total=7, Milk -2 total=4");
    }

    @Test
    void purchase_of_unknown_product_returns_409() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Ghost","quantity":1}]}
                """)
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("Ghost: not in stock");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_REJECTED"))
            .containsExactly("online: Ghost: not in stock");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED")).isEmpty();

        assertThat(given().get("/api/products").asString()).isEqualTo("[]");
    }

    @Test
    void purchase_exceeding_stock_returns_409_and_deducts_nothing() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Milk", ProductType.DAIRY, 6);


        auditHelper.clearAuditLog();

        // all-or-nothing: Apple is in stock, but is not deducted either
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":3},{"productName":"Milk","quantity":7}]}
                """)
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.jsonPath().<String>getList("$"))
            .containsExactly("Milk: only 6 in stock (requested 7)");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED")).isEmpty();
        assertThat(given().get("/api/products").asString())
            .contains("\"name\":\"Apple\",\"type\":\"FRUIT\",\"availableAmount\":10")
            .contains("\"name\":\"Milk\",\"type\":\"DAIRY\",\"availableAmount\":6");
    }

    @Test
    void repeated_product_is_checked_against_its_total_quantity() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 5);

        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":3},{"productName":"Apple","quantity":3}]}
                """)
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.jsonPath().<String>getList("$"))
            .containsExactly("Apple: only 5 in stock (requested 6)");
    }

    @Test
    void concurrent_purchases_never_sell_more_than_is_in_stock() throws Exception {
        int stock = 5;
        int customers = 20;
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, stock);

        // all customers are released at once, so their checkouts really overlap
        var start = new CountDownLatch(1);
        var statusCodes = new ArrayList<Future<Integer>>();
        try (var executor = Executors.newFixedThreadPool(customers)) {
            for (int i = 0; i < customers; i++) {
                statusCodes.add(executor.submit(() -> {
                    start.await();
                    return given()
                        .contentType(ContentType.JSON)
                        .body("""
                            {"items":[{"productName":"Apple","quantity":1}]}
                            """)
                        .post("/api/products/purchase")
                        .statusCode();
                }));
            }
            start.countDown();
        }

        var codes = new ArrayList<Integer>();
        for (var statusCode : statusCodes) codes.add(statusCode.get());
        assertThat(codes).filteredOn(c -> c == 204).hasSize(stock);
        assertThat(codes).filteredOn(c -> c == 409).hasSize(customers - stock);
        assertThat(given().get("/api/products").asString())
            .isEqualTo("""
                [{"name":"Apple","type":"FRUIT","availableAmount":0}]""");
    }

    @Test
    void purchase_above_limit_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":51}]}
                """)
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$"))
            .containsExactly("items[0]: must be less than or equal to 50");
    }

    @Test
    void purchase_with_invalid_item_returns_400_and_deducts_nothing() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);


        auditHelper.clearAuditLog();

        // all-or-nothing: the valid first item is not deducted either; a negative quantity used to *add* stock
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":3},{"productName":"Apple","quantity":-5}]}
                """)
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).contains("items[1]: must be greater than or equal to 1");
        assertThat(auditHelper.findEventDetails("ProductApiReceiver: PURCHASE_RECEIVED"))
            .containsExactly("Apple qty=3, Apple qty=-5");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_PROCESSING")).isEmpty();
        assertThat(given().get("/api/products").asString())
            .isEqualTo("""
                [{"name":"Apple","type":"FRUIT","availableAmount":10}]""");
    }
}
