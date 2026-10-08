package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.cashpoint.PurchaseMessage;
import org.svenehrke.triptychdemo.cross.cashpoint.PurchaseMessageItem;
import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.test.junit.QuarkusTest;
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
    @Inject InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/locations/bern/products").asString().equals("[]")
                && auditHelper.isEmpty();
        });
    }

    @Test
    void kafka_purchase_event_deducts_the_stores_inventory_only() {
        inventory.addAmount(Locations.BERN, "Orange", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ZURICH, "Orange", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.ONLINE, "Orange", ProductType.FRUIT, 10);

        cashpointPublisher.publish(new PurchaseMessage("bern", List.of(new PurchaseMessageItem("Orange", 4))));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(auditHelper.findEventDetails("CashpointReceiver: PURCHASE_RECEIVED"))
                .containsExactly("bern: Orange qty=4");
            assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_PROCESSING"))
                .containsExactly("bern: Orange qty=4");
            assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
                .containsExactly("bern: Orange -4 total=6");
        });
        assertThat(given().get("/api/locations/bern/products").asString())
            .isEqualTo("""
                [{"name":"Orange","type":"FRUIT","availableAmount":6}]""");
        assertThat(given().get("/api/locations/zurich/products").asString()).contains("\"availableAmount\":10");
        assertThat(given().get("/api/products").asString()).contains("\"availableAmount\":10");
    }

    @Test
    void store_sale_is_recorded_for_the_purchases_table_also_with_a_stock_discrepancy() {
        inventory.addAmount(Locations.BERN, "Orange", ProductType.FRUIT, 3);
        inventory.addAmount(Locations.BERN, "Apple", ProductType.FRUIT, 10);

        // Orange twice: one product, 5 units - more than the 3 on record
        cashpointPublisher.publish(new PurchaseMessage("bern", List.of(new PurchaseMessageItem("Orange", 4),
            new PurchaseMessageItem("Apple", 2), new PurchaseMessageItem("Orange", 1))));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var json = given().get("/locations/bern/inventory-fragment").jsonPath();
            assertThat(json.getList("vm.purchases.products")).containsExactly(2);
            assertThat(json.getList("vm.purchases.units")).containsExactly(7);
        });
        assertThat(auditHelper.findEventDetails("PurchaseHandler: STOCK_DISCREPANCY")).hasSize(1);
        assertThat(given().get("/locations/zurich/inventory-fragment").jsonPath().getList("vm.purchases")).isEmpty();
    }

    @Test
    void invalid_kafka_purchase_event_is_logged_and_deducts_nothing() {
        inventory.addAmount(Locations.BERN, "Orange", ProductType.FRUIT, 10);

        cashpointPublisher.publish(new PurchaseMessage("bern", List.of(new PurchaseMessageItem("Orange", -4))));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("CashpointReceiver: INVALID"))
                .containsExactly("Orange qty=-4: items[0]: must be greater than or equal to 1")
        );
        assertThat(auditHelper.findEventDetails("CashpointReceiver: PURCHASE_RECEIVED"))
            .containsExactly("bern: Orange qty=-4");
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_PROCESSING")).isEmpty();
        assertThat(given().get("/api/locations/bern/products").asString())
            .isEqualTo("""
                [{"name":"Orange","type":"FRUIT","availableAmount":10}]""");
    }

    @Test
    void purchase_event_of_an_unknown_or_non_store_location_is_logged_and_deducts_nothing() {
        inventory.addAmount(Locations.ONLINE, "Orange", ProductType.FRUIT, 10);

        cashpointPublisher.publish(new PurchaseMessage("paris", List.of(new PurchaseMessageItem("Orange", 1))));
        cashpointPublisher.publish(new PurchaseMessage("online", List.of(new PurchaseMessageItem("Orange", 1))));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("CashpointReceiver: INVALID")).containsExactlyInAnyOrder(
                "Orange qty=1: storeId 'paris' is not a store",
                "Orange qty=1: storeId 'online' is not a store")
        );
        assertThat(auditHelper.findEventDetails("PurchaseHandler: PURCHASE_PROCESSING")).isEmpty();
        assertThat(given().get("/api/products").asString()).contains("\"availableAmount\":10");
    }

    @Test
    void store_sale_beyond_recorded_stock_is_recorded_capped_at_zero_and_logged_as_discrepancy() {
        inventory.addAmount(Locations.BERN, "Orange", ProductType.FRUIT, 3);

        // the goods physically left the store, so the sale is not rejected - the inventory was wrong
        cashpointPublisher.publish(new PurchaseMessage("bern", List.of(new PurchaseMessageItem("Orange", 5))));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(auditHelper.findEventDetails("PurchaseHandler: STOCK_DISCREPANCY"))
                .containsExactly("bern: Orange: sold 5, only 3 on record");
            assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED"))
                .containsExactly("bern: Orange -5 total=0");
        });
        assertThat(given().get("/api/locations/bern/products").asString())
            .isEqualTo("""
                [{"name":"Orange","type":"FRUIT","availableAmount":0}]""");
    }

    @Test
    void store_sale_of_unknown_product_is_logged_as_discrepancy() {
        cashpointPublisher.publish(new PurchaseMessage("bern", List.of(new PurchaseMessageItem("Ghost", 1))));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("PurchaseHandler: STOCK_DISCREPANCY"))
                .containsExactly("bern: Ghost: sold 1, only 0 on record")
        );
        assertThat(auditHelper.findEventDetails("PurchaseHandler: INVENTORY_DEDUCTED")).isEmpty();
        assertThat(given().get("/api/locations/bern/products").asString()).isEqualTo("[]");
    }
}
