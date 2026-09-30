package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.cashpoint.PurchaseMessage;
import org.svenehrke.triptychdemo.cross.cashpoint.PurchaseMessageItem;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class CashpointViaKafkaFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject TestCashpointPublisher cashpointPublisher;

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

        cashpointPublisher.publish(new PurchaseMessage(List.of(new PurchaseMessageItem("Orange", 4))));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_PROCESSING"))
                .containsExactly("Orange qty=4");
            assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
                .containsExactly("Orange -4 total=6");

            assertThat(given().get("/api/products").asString())
                .isEqualTo("""
                    [{"name":"Orange","type":"FRUIT","availableAmount":6}]""");
        });
    }

    @Test
    void invalid_kafka_purchase_event_is_logged_and_deducts_nothing() {
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

        cashpointPublisher.publish(new PurchaseMessage(List.of(new PurchaseMessageItem("Orange", -4))));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("CashpointReceiver: INVALID"))
                .containsExactly("Orange qty=-4: items[0]: must be greater than or equal to 1")
        );
        assertThat(auditHelper.findEventDetails("CashpointReceiver: PURCHASE_RECEIVED"))
            .containsExactly("Orange qty=-4");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_PROCESSING")).isEmpty();
        assertThat(given().get("/api/products").asString())
            .isEqualTo("""
                [{"name":"Orange","type":"FRUIT","availableAmount":10}]""");
    }

    @Test
    void store_sale_beyond_recorded_stock_is_recorded_capped_at_zero_and_logged_as_discrepancy() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"productName": "Orange", "quantity": 3}
                """)
            .post("/api/products/order-fruits")
            .then().statusCode(204);

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(given().get("/api/products").asString())
                .isEqualTo("""
                    [{"name":"Orange","type":"FRUIT","availableAmount":3}]""")
        );

        auditHelper.clearAuditLog();

        // the goods physically left the store, so the sale is not rejected - the inventory was wrong
        cashpointPublisher.publish(new PurchaseMessage(List.of(new PurchaseMessageItem("Orange", 5))));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(auditHelper.findEventDetails("PurchaseHandler: STOCK_DISCREPANCY"))
                .containsExactly("Orange: sold 5, only 3 on record");
            assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
                .containsExactly("Orange -5 total=0");
        });
        assertThat(given().get("/api/products").asString())
            .isEqualTo("""
                [{"name":"Orange","type":"FRUIT","availableAmount":0}]""");
    }

    @Test
    void store_sale_of_unknown_product_is_logged_as_discrepancy() {
        cashpointPublisher.publish(new PurchaseMessage(List.of(new PurchaseMessageItem("Ghost", 1))));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("PurchaseHandler: STOCK_DISCREPANCY"))
                .containsExactly("Ghost: sold 1, only 0 on record")
        );
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED")).isEmpty();
        assertThat(given().get("/api/products").asString()).isEqualTo("[]");
    }
}
