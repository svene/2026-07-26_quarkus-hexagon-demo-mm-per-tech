package com.example.hexarcdemo.server;

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
class CustomerPurchaseFlowTest {

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
        // First stock up via a fruit order so there is something to purchase.
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"productName": "Apple", "quantity": 10}
                """)
            .post("/api/products/order-fruits")
            .then().statusCode(204);

        // Wait for the delivery to arrive via Kafka and land in inventory.
        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(given().get("/api/products").asString())
                .isEqualTo("""
                    [{"name":"Apple","type":"FRUIT","availableAmount":10}]""")
        );

        auditHelper.clearAuditLog();

        // Simulate a customer buying 3 apples via the REST endpoint.
        var purchaseResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {"productName": "Apple", "quantity": 3}
                """)
            .post("/api/products/purchase");
        assertThat(purchaseResponse.statusCode()).isEqualTo(204);

        // PURCHASE_RECEIVED and INVENTORY_DEDUCTED are written synchronously
        // inside PurchaseHandler during the HTTP request.
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_RECEIVED"))
            .containsExactly("Apple qty=3");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
            .containsExactly("Apple -3 total=7");

        // Verify the inventory reflects the deduction.
        assertThat(given().get("/api/products").asString())
            .isEqualTo("""
                [{"name":"Apple","type":"FRUIT","availableAmount":7}]""");
    }

    @Test
    void purchase_of_unknown_product_logs_product_not_found() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"productName": "Ghost", "quantity": 1}
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
