package com.example.hexdemo.server;

import com.example.hexdemo.core.domain.ProductType;
import com.example.hexdemo.core.port.out.InventoryRepositorySPI;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class ProductReceiverTest {

    @Inject TestInventoryHelper helper;
    @Inject InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            helper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
    }

    @Test
    void get_empty_inventory_shows_no_products_message() {
        var response = given().get("/products");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString()).contains("No products in inventory yet.");
    }

    @Test
    void get_with_products_shows_them_in_html_table() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Cola", ProductType.BEVERAGE, 5);

        var response = given().get("/products");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("Apple", "FRUIT", "10")
            .contains("Cola", "BEVERAGE", "5");
    }

    @Test
    void order_fruits_form_post_redirects_to_products() {
        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", 20)
            .redirects().follow(false)
            .post("/products/order-fruits");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).contains("/products");
    }

    @Test
    void order_beverages_form_post_redirects_to_products() {
        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "OrangeJuice")
            .formParam("quantity", 12)
            .redirects().follow(false)
            .post("/products/order-beverages");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).contains("/products");
    }
}
