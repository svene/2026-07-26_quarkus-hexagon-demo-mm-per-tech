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
class VegetableOrderDeliveryFlowTest {

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
    void ordered_vegetables_arrive_via_kafka_and_appear_in_inventory() {
        var orderResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Carrot",
                  "quantity": 8
                }
                """)
            .post("/api/products/order-vegetables");
        assertThat(orderResponse.statusCode()).isEqualTo(204);

        assertThat(auditHelper.findEventDetails("VegetablesHandler: VEGETABLES_ORDER_RECEIVED"))
            .containsExactly("Carrot qty=8");
        assertThat(auditHelper.findEventDetails("VegetablesHandler: VEGETABLES_ORDER_PLACED"))
            .containsExactly("Carrot qty=8");

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var response = given().get("/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("""
                [{"name":"Carrot","type":"VEGETABLE","availableAmount":8}]""");

            assertThat(auditHelper.findEventDetails("InventoryHandler: VEGETABLE_DELIVERY_RECEIVED"))
                .containsExactly("Carrot qty=8");
            assertThat(auditHelper.findEventDetails("InventoryHandler: VEGETABLE_INVENTORY_UPDATED"))
                .containsExactly("Carrot +8 total=8");
        });
    }
}
