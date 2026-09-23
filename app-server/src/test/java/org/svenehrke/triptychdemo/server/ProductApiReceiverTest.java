package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;

import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class ProductApiReceiverTest {

    @Inject TestInventoryHelper helper;
    @Inject
    InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            helper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
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
    void order_fruits_with_blank_product_name_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": " ",
                  "quantity": 5
                }
                """)
            .post("/api/products/order-fruits");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must not be blank");
    }

    @Test
    void order_fruits_with_non_positive_quantity_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Mango",
                  "quantity": 0
                }
                """)
            .post("/api/products/order-fruits");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must be greater than or equal to 1");
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

    @Test
    void order_beverages_with_blank_product_name_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": " ",
                  "quantity": 3
                }
                """)
            .post("/api/products/order-beverages");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must not be blank");
    }

    @Test
    void order_beverages_with_non_positive_quantity_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Coffee",
                  "quantity": 0
                }
                """)
            .post("/api/products/order-beverages");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must be greater than or equal to 1");
    }

    @Test
    void order_vegetables_calls_supplier_and_returns_204() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Carrot",
                  "quantity": 8
                }
                """)
            .post("/api/products/order-vegetables");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void order_vegetables_with_blank_product_name_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": " ",
                  "quantity": 8
                }
                """)
            .post("/api/products/order-vegetables");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must not be blank");
    }

    @Test
    void order_vegetables_with_non_positive_quantity_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Carrot",
                  "quantity": 0
                }
                """)
            .post("/api/products/order-vegetables");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must be greater than or equal to 1");
    }

    @Test
    void order_dairy_calls_supplier_and_returns_204() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Milk",
                  "quantity": 6
                }
                """)
            .post("/api/products/order-dairy");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void order_dairy_with_blank_product_name_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": " ",
                  "quantity": 6
                }
                """)
            .post("/api/products/order-dairy");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must not be blank");
    }

    @Test
    void order_dairy_with_non_positive_quantity_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Milk",
                  "quantity": 0
                }
                """)
            .post("/api/products/order-dairy");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must be greater than or equal to 1");
    }

    @Test
    void order_meat_calls_supplier_and_returns_204() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Steak",
                  "quantity": 4
                }
                """)
            .post("/api/products/order-meat");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void order_meat_with_blank_product_name_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": " ",
                  "quantity": 4
                }
                """)
            .post("/api/products/order-meat");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must not be blank");
    }

    @Test
    void order_meat_with_non_positive_quantity_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Steak",
                  "quantity": 0
                }
                """)
            .post("/api/products/order-meat");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must be greater than or equal to 1");
    }

    @Test
    void order_bakery_calls_supplier_and_returns_204() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Bread",
                  "quantity": 2
                }
                """)
            .post("/api/products/order-bakery");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void order_bakery_with_blank_product_name_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": " ",
                  "quantity": 2
                }
                """)
            .post("/api/products/order-bakery");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must not be blank");
    }

    @Test
    void order_bakery_with_non_positive_quantity_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Bread",
                  "quantity": 0
                }
                """)
            .post("/api/products/order-bakery");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must be greater than or equal to 1");
    }

    @Test
    void order_nonfood_calls_supplier_and_returns_204() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Detergent",
                  "quantity": 9
                }
                """)
            .post("/api/products/order-nonfood");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void order_nonfood_with_blank_product_name_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": " ",
                  "quantity": 9
                }
                """)
            .post("/api/products/order-nonfood");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must not be blank");
    }

    @Test
    void order_nonfood_with_non_positive_quantity_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Detergent",
                  "quantity": 0
                }
                """)
            .post("/api/products/order-nonfood");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must be greater than or equal to 1");
    }

    // --- Request structure (checked before parse()) ---

    @Test
    void order_fruits_with_empty_body_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .post("/api/products/order-fruits");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("request body is required");
    }

    @Test
    void purchase_with_empty_body_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("request body is required");
    }

    @Test
    void purchase_without_items_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("{}")
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("items is required");
    }

    @Test
    void purchase_with_null_item_returns_400() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":1},null]}
                """)
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("items[1]: must not be null");
    }
}
