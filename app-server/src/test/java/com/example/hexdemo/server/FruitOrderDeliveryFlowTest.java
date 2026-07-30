package com.example.hexdemo.server;

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
class FruitOrderDeliveryFlowTest {

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
    void ordered_fruits_arrive_via_kafka_and_appear_in_inventory() {
        var orderResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Mango",
                  "quantity": 5
                }
                """)
            .post("/api/products/order-fruits");
        assertThat(orderResponse.statusCode()).isEqualTo(204);

        // Both written synchronously inside FruitsHandler during the HTTP request:
        // FRUITS_ORDER_RECEIVED proves ProductApiReceiver delegated to FruitsHandler.
        // FRUITS_ORDER_PLACED proves FruitsHandler called FruitSupplierSPI.
        assertThat(auditHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_RECEIVED"))
            .containsExactly("Mango qty=5");
        assertThat(auditHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_PLACED"))
            .containsExactly("Mango qty=5");

        // Wait for: FruitSupplierStub → Kafka → FruitDeliveryReceiver → InventoryHandler.
        // FRUIT_DELIVERY_RECEIVED and INVENTORY_UPDATED are inside untilAsserted because
        // InventoryHandler commits to Postgres before writing to MongoDB — the inventory
        // may be visible slightly before the audit entries appear.
        await().atMost(10, SECONDS).untilAsserted(() -> {
            var response = given().get("/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("""
                [{"name":"Mango","type":"FRUIT","availableAmount":5}]""");

            // FRUIT_DELIVERY_RECEIVED proves FruitDeliveryReceiver delegated to InventoryHandler.
            assertThat(auditHelper.findEventDetails("InventoryHandler: FRUIT_DELIVERY_RECEIVED"))
                .containsExactly("Mango qty=5");
            // FRUIT_INVENTORY_UPDATED proves InventoryHandler called InventoryRepositorySPI.
            assertThat(auditHelper.findEventDetails("InventoryHandler: FRUIT_INVENTORY_UPDATED"))
                .containsExactly("Mango +5 total=5");
        });
    }
}
