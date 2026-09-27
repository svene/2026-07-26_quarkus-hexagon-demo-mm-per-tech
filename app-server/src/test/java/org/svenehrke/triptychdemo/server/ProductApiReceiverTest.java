package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;

import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
        order-fruits | missing quantity        | {"productName": "Mango"}                                      | quantity is required
        order-fruits | null quantity           | {"productName": "Mango", "quantity": null}                    | quantity is required
        order-fruits | missing productName     | {"quantity": 5}                                               | productName is required
        """)
    void missing_field_returns_400(String endpoint, String description, String body, String expectedMessage) {
        var response = given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/api/products/" + endpoint);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly(expectedMessage);
    }

    @Test
    void order_fruits_reports_every_missing_field() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("{}")
            .post("/api/products/order-fruits");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$"))
            .containsExactly("productName is required", "quantity is required");
    }

    @Test
    void purchase_reports_every_structure_error_of_every_item() {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items": [{"productName": "A"}, {"quantity": null}, null]}
                """)
            .post("/api/products/purchase");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly(
            "items[0].quantity is required",
            "items[1].productName is required",
            "items[1].quantity is required",
            "items[2]: must not be null");
    }

    // --- Deserialization errors (rejected by Jackson before the receiver method runs) ---

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
        order-fruits | non-numeric quantity    | {"productName": "Mango", "quantity": "abc"}                   | quantity: must be an integer
        order-fruits | numeric string quantity | {"productName": "Mango", "quantity": "5"}                     | quantity: must be an integer
        order-fruits | decimal quantity        | {"productName": "Mango", "quantity": 5.7}                     | quantity: must be an integer
        order-fruits | object as quantity      | {"productName": "Mango", "quantity": {}}                      | quantity: must be an integer
        order-fruits | out-of-range quantity   | {"productName": "Mango", "quantity": 99999999999}             | quantity: number is out of range
        order-fruits | number as productName   | {"productName": 123, "quantity": 5}                           | productName: must be a string
        order-fruits | list as body            | []                                                            | request body: has an invalid value
        order-fruits | invalid JSON            | {not json                                                     | request body is not valid JSON
        purchase     | non-numeric item qty    | {"items": [{"productName": "Mango", "quantity": "abc"}]}      | items[0].quantity: must be an integer
        purchase     | items is not a list     | {"items": "Mango"}                                            | items: must be a list
        purchase     | invalid JSON            | {"items": [                                                   | request body is not valid JSON
        """)
    void malformed_input_returns_400_with_json_messages(String endpoint, String description, String body, String expectedMessage) {
        var response = given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/api/products/" + endpoint);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.jsonPath().<String>getList("$")).containsExactly(expectedMessage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"order-fruits", "order-vegetables", "order-dairy", "order-beverages", "order-meat", "order-bakery", "order-nonfood"})
    void order_above_limit_returns_400(String endpoint) {
        var response = given()
            .contentType(ContentType.JSON)
            .body("""
                {"productName": "Mango", "quantity": 2001}
                """)
            .post("/api/products/" + endpoint);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().<String>getList("$")).containsExactly("must be less than or equal to 2000");
    }
}
