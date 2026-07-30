package com.example.hexademo.server;

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
class CashpointFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;

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
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"productName": "Apple", "quantity": 10}
                """)
            .post("/api/products/order-fruits")
            .then().statusCode(204);

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(given().get("/api/products").asString())
                .isEqualTo("""
                    [{"name":"Apple","type":"FRUIT","availableAmount":10}]""")
        );

        auditHelper.clearAuditLog();

        var purchaseResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":3}]}
                """)
            .post("/api/products/purchase");
        assertThat(purchaseResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_RECEIVED"))
            .containsExactly("Apple qty=3");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
            .containsExactly("Apple -3 total=7");

        assertThat(given().get("/api/products").asString())
            .isEqualTo("""
                [{"name":"Apple","type":"FRUIT","availableAmount":7}]""");
    }

    @Test
    void multi_item_purchase_deducts_each_product() {
        given().contentType(ContentType.JSON)
            .body("""
                {"productName": "Apple", "quantity": 10}
                """)
            .post("/api/products/order-fruits").then().statusCode(204);
        given().contentType(ContentType.JSON)
            .body("""
                {"productName": "Milk", "quantity": 6}
                """)
            .post("/api/products/order-dairy").then().statusCode(204);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var body = given().get("/api/products").asString();
            assertThat(body).contains("Apple").contains("Milk");
        });

        auditHelper.clearAuditLog();

        var purchaseResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Apple","quantity":3},{"productName":"Milk","quantity":2}]}
                """)
            .post("/api/products/purchase");
        assertThat(purchaseResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_RECEIVED"))
            .containsExactly("Apple qty=3, Milk qty=2");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
            .containsExactly("Apple -3 total=7, Milk -2 total=4");
    }

    @Test
    void purchase_of_unknown_product_logs_product_not_found() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"items":[{"productName":"Ghost","quantity":1}]}
                """)
            .post("/api/products/purchase")
            .then().statusCode(204);

        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_RECEIVED"))
            .containsExactly("Ghost qty=1");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PRODUCT_NOT_FOUND"))
            .containsExactly("Ghost");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
            .isEmpty();

        assertThat(given().get("/api/products").asString()).isEqualTo("[]");
    }
}
