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
class MeatOrderDeliveryFlowTest {

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
    void ordered_meat_arrives_via_kafka_and_appears_in_inventory() {
        var orderResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Chicken",
                  "quantity": 4
                }
                """)
            .post("/api/products/order-meat");
        assertThat(orderResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("MeatHandler: MEAT_ORDER_RECEIVED"))
            .containsExactly("Chicken qty=4");
        assertThat(auditHelper.findEventDetails("MeatHandler: MEAT_ORDER_PLACED"))
            .containsExactly("Chicken qty=4");

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var response = given().get("/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("""
                [{"name":"Chicken","type":"MEAT","availableAmount":4}]""");

            assertThat(auditHelper.findEventDetails("InventoryHandler: MEAT_DELIVERY_RECEIVED"))
                .containsExactly("Chicken qty=4");
            assertThat(auditHelper.findEventDetails("InventoryHandler: MEAT_INVENTORY_UPDATED"))
                .containsExactly("Chicken +4 total=4");
        });
    }
}
