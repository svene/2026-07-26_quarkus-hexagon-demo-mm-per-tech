package com.example.hexdemo.server;

import com.example.hexdemo.core.domain.ProductType;
import com.example.hexdemo.core.port.out.InventoryRepositorySPI;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ProductApiReceiverTest {

    @Inject TestInventoryHelper helper;
    @Inject InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        helper.resetInventory();
    }

    @Test
    void list_empty_inventory_returns_empty_json_array() {
        given()
            .when().get("/api/products")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", is(0));
    }

    @Test
    void list_returns_product_as_json() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);

        given()
            .when().get("/api/products")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", is(1))
            .body("[0].name", is("Apple"))
            .body("[0].type", is("FRUIT"))
            .body("[0].availableAmount", is(10));
    }

    @Test
    void list_returns_all_products() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Banana", ProductType.FRUIT, 7);
        inventory.addAmount("Cola", ProductType.BEVERAGE, 20);

        given()
            .when().get("/api/products")
            .then()
            .statusCode(200)
            .body("size()", is(3));
    }

    @Test
    void order_fruits_calls_supplier_and_returns_204() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"productName\":\"Mango\",\"quantity\":5}")
            .when().post("/api/products/order-fruits")
            .then()
            .statusCode(204);
    }

    @Test
    void order_beverages_calls_supplier_and_returns_204() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"productName\":\"Coffee\",\"quantity\":3}")
            .when().post("/api/products/order-beverages")
            .then()
            .statusCode(204);
    }
}
