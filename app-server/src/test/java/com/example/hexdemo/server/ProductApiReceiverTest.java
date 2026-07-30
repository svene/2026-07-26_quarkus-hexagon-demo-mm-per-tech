package com.example.hexdemo.server;

import com.example.hexdemo.core.domain.ProductType;
import com.example.hexdemo.core.port.out.InventoryRepositorySPI;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

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
        var response = given().get("/api/products");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.asString()).isEqualTo("[]");
    }

    @Test
    void list_returns_product_as_json() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);

        var response = given().get("/api/products");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo("""
            [{"name":"Apple","type":"FRUIT","availableAmount":10}]""");
    }

    @Test
    void list_returns_all_products() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Banana", ProductType.FRUIT, 7);
        inventory.addAmount("Cola", ProductType.BEVERAGE, 20);

        var response = given().get("/api/products");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().<Object>getList("$")).hasSize(3);
    }

    @Test
    void order_fruits_calls_supplier_and_returns_204() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Mango",
                  "quantity": 5
                }
                """)
            .post("/api/products/order-fruits");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void order_beverages_calls_supplier_and_returns_204() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Coffee",
                  "quantity": 3
                }
                """)
            .post("/api/products/order-beverages");

        assertThat(response.statusCode()).isEqualTo(204);
    }
}
