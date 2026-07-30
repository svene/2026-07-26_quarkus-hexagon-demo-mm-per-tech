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
class CustomerPurchaseViaKafkaFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject TestKafkaPurchasePublisher purchasePublisher;

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
    void kafka_purchase_event_deducts_inventory() {
        // Stock up first so there is something to deduct.
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"productName": "Orange", "quantity": 10}
                """)
            .post("/api/products/order-fruits")
            .then().statusCode(204);

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(given().get("/api/products").asString())
                .isEqualTo("""
                    [{"name":"Orange","type":"FRUIT","availableAmount":10}]""")
        );

        auditHelper.clearAuditLog();

        // Simulate a purchase event arriving via the customer-purchases Kafka topic.
        purchasePublisher.publish("Orange", 4);

        // CustomerPurchaseReceiver → PurchaseHandler logs are async (Kafka path).
        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_RECEIVED"))
                .containsExactly("Orange qty=4");
            assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
                .containsExactly("Orange -4 total=6");

            assertThat(given().get("/api/products").asString())
                .isEqualTo("""
                    [{"name":"Orange","type":"FRUIT","availableAmount":6}]""");
        });
    }
}
