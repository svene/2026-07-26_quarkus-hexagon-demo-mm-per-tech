package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;

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
    @Inject
    InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
    }

    @Test
    void get_empty_inventory_shows_no_products_message() {
        var response = given().get("/shop");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString()).contains("No products available to purchase right now.");
    }

    @Test
    void get_with_products_shows_a_cart_row_per_product() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Milk", ProductType.DAIRY, 6);

        var response = given().get("/shop");
        var body = response.asString();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body).contains("Apple", "FRUIT", "Milk", "DAIRY");
        assertThat(body).contains("name=\"productName\" value=\"Apple\"");
        assertThat(body).contains("name=\"productName\" value=\"Milk\"");
        assertThat(body).contains("name=\"quantity\"");
    }

    @Test
    void checkout_deducts_inventory_for_submitted_items() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Milk", ProductType.DAIRY, 6);

        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Apple")
            .formParam("productName", "Milk")
            .formParam("quantity", "3")
            .formParam("quantity", "2")
            .redirects().follow(false)
            .post("/shop/checkout");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).contains("/shop");

        assertThat(given().get("/api/products").asString())
            .contains("\"name\":\"Apple\",\"type\":\"FRUIT\",\"availableAmount\":7")
            .contains("\"name\":\"Milk\",\"type\":\"DAIRY\",\"availableAmount\":4");
    }

    @Test
    void checkout_with_invalid_rows_shows_errors_and_deducts_nothing() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Milk", ProductType.DAIRY, 6);
        inventory.addAmount("Bread", ProductType.BAKERY, 4);

        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Apple")
            .formParam("productName", "Milk")
            .formParam("productName", "Bread")
            .formParam("quantity", "3")
            .formParam("quantity", "-2")
            .formParam("quantity", "abc")
            .redirects().follow(false)
            .post("/shop/checkout");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("Purchase not processed")
            .contains("Milk: must be greater than or equal to 1")
            .contains("Bread: quantity must be a number");

        assertThat(given().get("/api/products").asString())
            .contains("\"name\":\"Apple\",\"type\":\"FRUIT\",\"availableAmount\":10")
            .contains("\"name\":\"Milk\",\"type\":\"DAIRY\",\"availableAmount\":6");
    }

    @Test
    void checkout_ignores_blank_and_zero_quantity_rows() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Milk", ProductType.DAIRY, 6);

        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Apple")
            .formParam("productName", "Milk")
            .formParam("quantity", "")
            .formParam("quantity", "0")
            .redirects().follow(false)
            .post("/shop/checkout");

        assertThat(response.statusCode()).isEqualTo(303);

        assertThat(given().get("/api/products").asString())
            .contains("\"name\":\"Apple\",\"type\":\"FRUIT\",\"availableAmount\":10")
            .contains("\"name\":\"Milk\",\"type\":\"DAIRY\",\"availableAmount\":6");
    }

    @Test
    void inventory_fragment_returns_a_partial_per_product() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);

        var response = given().get("/shop/inventory-fragment");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("<hx-partial id=\"avail-Apple\"")
            .contains(">10<");
    }
}
