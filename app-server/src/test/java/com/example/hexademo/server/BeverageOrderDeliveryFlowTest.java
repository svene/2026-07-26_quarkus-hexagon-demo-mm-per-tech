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
class BeverageOrderDeliveryFlowTest {

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
    void ordered_beverages_arrive_via_kafka_and_appear_in_inventory() {
        var orderResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Cola",
                  "quantity": 12
                }
                """)
            .post("/api/products/order-beverages");
        assertThat(orderResponse.statusCode()).isEqualTo(204);

        // Both written synchronously inside BeveragesHandler during the HTTP request:
        // BEVERAGES_ORDER_RECEIVED proves ProductApiReceiver delegated to BeveragesHandler.
        // BEVERAGES_ORDER_PLACED proves BeveragesHandler called BeverageSupplierSPI.
        assertThat(auditHelper.findEventDetails("BeveragesHandler: BEVERAGES_ORDER_RECEIVED"))
            .containsExactly("Cola qty=12");
        assertThat(auditHelper.findEventDetails("BeveragesHandler: BEVERAGES_ORDER_PLACED"))
            .containsExactly("Cola qty=12");

        // Wait for: BeverageSupplierStub → Kafka → BeveragesDeliveryReceiver → InventoryHandler.
        // BEVERAGE_DELIVERY_RECEIVED and BEVERAGE_INVENTORY_UPDATED are inside untilAsserted because
        // BeveragesDeliveryReceiver writes to MongoDB around its call into InventoryAPI, which commits
        // to Postgres — the inventory may be visible slightly before the audit entries appear.
        await().atMost(10, SECONDS).untilAsserted(() -> {
            var response = given().get("/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("""
                [{"name":"Cola","type":"BEVERAGE","availableAmount":12}]""");

            // BEVERAGE_DELIVERY_RECEIVED proves BeveragesDeliveryReceiver called updateBeverageAmount.
            assertThat(auditHelper.findEventDetails("BeveragesDeliveryReceiver: BEVERAGE_DELIVERY_RECEIVED"))
                .containsExactly("Cola qty=12");
            // BEVERAGE_INVENTORY_UPDATED proves BeveragesDeliveryReceiver's call to InventoryAPI returned.
            assertThat(auditHelper.findEventDetails("BeveragesDeliveryReceiver: BEVERAGE_INVENTORY_UPDATED"))
                .containsExactly("Cola +12");
        });
    }
}
