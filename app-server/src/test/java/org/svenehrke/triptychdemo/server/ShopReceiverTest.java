package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class ShopReceiverTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditLogHelper;
    @Inject
    InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
        await().atMost(5, SECONDS).until(() -> {
            auditLogHelper.clearAuditLog();
            return auditLogHelper.isEmpty();
        });
    }

    @Test
    void get_shop_serves_the_page_shell_that_loads_the_page_view() {
        var response = given().get("/shop");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("<script src=\"/js/hono/hx-hono.js\">")
            .contains("hx-get=\"/shop/page\"");
    }

    @Test
    void page_view_with_empty_inventory_has_no_products() {
        var response = given().get("/shop/page");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.jsonPath().getString("route")).isEqualTo("ShopPage");
        assertThat(response.jsonPath().getList("vm.products")).isEmpty();
        assertThat(response.jsonPath().getList("vm.errors")).isEmpty();
    }

    @Test
    void page_view_lists_only_products_in_stock() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Milk", ProductType.DAIRY, 6);
        inventory.addAmount(Locations.ONLINE, "Bread", ProductType.BAKERY, 0);

        var json = given().get("/shop/page").jsonPath();

        assertThat(json.getList("vm.products.name")).containsExactlyInAnyOrder("Apple", "Milk");
        assertThat(json.getString("vm.products.find { it.name == 'Milk' }.type")).isEqualTo("DAIRY");
    }

    @Test
    void checkout_deducts_inventory_for_submitted_items() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Milk", ProductType.DAIRY, 6);

        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Apple")
            .formParam("productName", "Milk")
            .formParam("quantity", "3")
            .formParam("quantity", "2")
            .post("/shop/checkout");

        // the fresh page, rendered into #app in place of the old one
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("route")).isEqualTo("ShopPage");
        assertThat(response.jsonPath().getList("vm.errors")).isEmpty();
        assertThat(auditLogHelper.findEventDetails("ShopReceiver: PURCHASE_RECEIVED"))
            .containsExactly("Apple qty=3, Milk qty=2");

        assertThat(given().get("/api/products").asString())
            .contains("\"name\":\"Apple\",\"type\":\"FRUIT\",\"availableAmount\":7")
            .contains("\"name\":\"Milk\",\"type\":\"DAIRY\",\"availableAmount\":4");
    }

    @Test
    void checkout_with_invalid_rows_shows_errors_and_deducts_nothing() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Milk", ProductType.DAIRY, 6);
        inventory.addAmount(Locations.ONLINE, "Bread", ProductType.BAKERY, 4);

        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Apple")
            .formParam("productName", "Milk")
            .formParam("productName", "Bread")
            .formParam("quantity", "3")
            .formParam("quantity", "-2")
            .formParam("quantity", "abc")
            .post("/shop/checkout");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("route")).isEqualTo("ShopPage");
        assertThat(response.jsonPath().getList("vm.errors"))
            .contains("Milk: must be greater than or equal to 1", "Bread: must be a number");

        assertThat(given().get("/api/products").asString())
            .contains("\"name\":\"Apple\",\"type\":\"FRUIT\",\"availableAmount\":10")
            .contains("\"name\":\"Milk\",\"type\":\"DAIRY\",\"availableAmount\":6");
    }

    @Test
    void checkout_ignores_blank_and_zero_quantity_rows() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Milk", ProductType.DAIRY, 6);

        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Apple")
            .formParam("productName", "Milk")
            .formParam("quantity", "")
            .formParam("quantity", "0")
            .post("/shop/checkout");

        assertThat(response.statusCode()).isEqualTo(200);
        // Blank rows are not in the cart and not logged; "0" is logged as submitted.
        assertThat(auditLogHelper.findEventDetails("ShopReceiver: PURCHASE_RECEIVED"))
            .containsExactly("Milk qty=0");

        assertThat(given().get("/api/products").asString())
            .contains("\"name\":\"Apple\",\"type\":\"FRUIT\",\"availableAmount\":10")
            .contains("\"name\":\"Milk\",\"type\":\"DAIRY\",\"availableAmount\":6");
    }

    @Test
    void inventory_fragment_returns_only_products_in_stock() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Bread", ProductType.BAKERY, 0);

        var response = given().get("/shop/inventory-fragment");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("route")).isEqualTo("ShopProducts");
        assertThat(response.jsonPath().getList("vm.products.name")).containsExactly("Apple");
        assertThat(response.jsonPath().getInt("vm.products[0].availableAmount")).isEqualTo(10);
    }

    @Test
    void page_and_inventory_fragment_list_products_ordered_by_name() {
        inventory.addAmount(Locations.ONLINE, "milk", ProductType.DAIRY, 6);
        inventory.addAmount(Locations.ONLINE, "Banana", ProductType.FRUIT, 3);
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);

        assertThat(given().get("/shop/page").jsonPath().getList("vm.products.name"))
            .containsExactly("Apple", "Banana", "milk");
        assertThat(given().get("/shop/inventory-fragment").jsonPath().getList("vm.products.name"))
            .containsExactly("Apple", "Banana", "milk");
    }

    @Test
    void checkout_exceeding_stock_returns_409_and_deducts_nothing() {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Milk", ProductType.DAIRY, 2);

        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Apple")
            .formParam("productName", "Milk")
            .formParam("quantity", "3")
            .formParam("quantity", "5")
            .post("/shop/checkout");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.jsonPath().getString("route")).isEqualTo("ShopPage");
        assertThat(response.jsonPath().getList("vm.errors")).containsExactly("Milk: only 2 in stock (requested 5)");

        assertThat(given().get("/api/products").asString())
            .contains("\"name\":\"Apple\",\"type\":\"FRUIT\",\"availableAmount\":10")
            .contains("\"name\":\"Milk\",\"type\":\"DAIRY\",\"availableAmount\":2");
    }
}
