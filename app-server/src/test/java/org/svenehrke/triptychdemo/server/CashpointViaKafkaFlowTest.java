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
            assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_RECEIVED"))
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
            assertThat(auditHelper.findEventDetails("CashpointReceiver: PURCHASE_RECEIVED"))
                .containsExactly("INVALID: Orange qty=-4: items[0]: must be greater than or equal to 1")
        );
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_RECEIVED")).isEmpty();
        assertThat(given().get("/api/products").asString())
            .isEqualTo("""
                [{"name":"Orange","type":"FRUIT","availableAmount":10}]""");
    }
}
