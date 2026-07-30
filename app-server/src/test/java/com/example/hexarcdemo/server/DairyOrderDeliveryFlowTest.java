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
class DairyOrderDeliveryFlowTest {

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
    void ordered_dairy_arrives_via_kafka_and_appears_in_inventory() {
        var orderResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Milk",
                  "quantity": 6
                }
                """)
            .post("/api/products/order-dairy");
        assertThat(orderResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("DairyHandler: DAIRY_ORDER_RECEIVED"))
            .containsExactly("Milk qty=6");
        assertThat(auditHelper.findEventDetails("DairyHandler: DAIRY_ORDER_PLACED"))
            .containsExactly("Milk qty=6");

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var response = given().get("/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("""
                [{"name":"Milk","type":"DAIRY","availableAmount":6}]""");

            assertThat(auditHelper.findEventDetails("InventoryHandler: DAIRY_DELIVERY_RECEIVED"))
                .containsExactly("Milk qty=6");
            assertThat(auditHelper.findEventDetails("InventoryHandler: DAIRY_INVENTORY_UPDATED"))
                .containsExactly("Milk +6 total=6");
        });
    }
}
