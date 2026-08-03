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
class BakeryOrderDeliveryFlowTest {

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
    void ordered_bakery_arrives_via_kafka_and_appears_in_inventory() {
        var orderResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Bread",
                  "quantity": 10
                }
                """)
            .post("/api/products/order-bakery");
        assertThat(orderResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("BakeryHandler: BAKERY_ORDER_RECEIVED"))
            .containsExactly("Bread qty=10");
        assertThat(auditHelper.findEventDetails("BakeryHandler: BAKERY_ORDER_PLACED"))
            .containsExactly("Bread qty=10");

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var response = given().get("/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("""
                [{"name":"Bread","type":"BAKERY","availableAmount":10}]""");

            assertThat(auditHelper.findEventDetails("BakeryDeliveryReceiver: BAKERY_DELIVERY_RECEIVED"))
                .containsExactly("Bread qty=10");
            assertThat(auditHelper.findEventDetails("BakeryDeliveryReceiver: BAKERY_INVENTORY_UPDATED"))
                .containsExactly("Bread +10");
        });
    }
}
