package com.example.hexdemo.server;

import com.example.hexdemo.core.domain.ProductType;
import com.example.hexdemo.core.port.out.InventoryRepositorySPI;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class ProductReceiverTest {

    @Inject TestInventoryHelper helper;
    @Inject InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        helper.resetInventory();
    }

    @Test
    void get_empty_inventory_shows_no_products_message() {
        given()
            .when().get("/products")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("No products in inventory yet."));
    }

    @Test
    void get_with_products_shows_them_in_html_table() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Cola", ProductType.BEVERAGE, 5);

        given()
            .when().get("/products")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("Apple"))
            .body(containsString("FRUIT"))
            .body(containsString("10"))
            .body(containsString("Cola"))
            .body(containsString("BEVERAGE"))
            .body(containsString("5"));
    }

    @Test
    void order_fruits_form_post_redirects_to_products() {
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", 20)
            .redirects().follow(false)
            .when().post("/products/order-fruits")
            .then()
            .statusCode(303)
            .header("Location", containsString("/products"));
    }

    @Test
    void order_beverages_form_post_redirects_to_products() {
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "OrangeJuice")
            .formParam("quantity", 12)
            .redirects().follow(false)
            .when().post("/products/order-beverages")
            .then()
            .statusCode(303)
            .header("Location", containsString("/products"));
    }
}
