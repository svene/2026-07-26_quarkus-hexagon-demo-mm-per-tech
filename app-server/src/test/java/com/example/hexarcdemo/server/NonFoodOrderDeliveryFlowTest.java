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
class NonFoodOrderDeliveryFlowTest {

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
    void ordered_nonfood_arrives_via_kafka_and_appears_in_inventory() {
        var orderResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Detergent",
                  "quantity": 3
                }
                """)
            .post("/api/products/order-nonfood");
        assertThat(orderResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("NonFoodHandler: NONFOOD_ORDER_RECEIVED"))
            .containsExactly("Detergent qty=3");
        assertThat(auditHelper.findEventDetails("NonFoodHandler: NONFOOD_ORDER_PLACED"))
            .containsExactly("Detergent qty=3");

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var response = given().get("/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("""
                [{"name":"Detergent","type":"NON_FOOD","availableAmount":3}]""");

            assertThat(auditHelper.findEventDetails("InventoryHandler: NON_FOOD_DELIVERY_RECEIVED"))
                .containsExactly("Detergent qty=3");
            assertThat(auditHelper.findEventDetails("InventoryHandler: NON_FOOD_INVENTORY_UPDATED"))
                .containsExactly("Detergent +3 total=3");
        });
    }
}
