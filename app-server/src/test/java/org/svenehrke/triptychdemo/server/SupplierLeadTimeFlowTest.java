package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.feature.fruit.FruitOrder;
import org.svenehrke.triptychdemo.feature.fruit.FruitsHandler;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodHandler;
import org.svenehrke.triptychdemo.feature.nonfood.NonFoodOrder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The supplier stubs deliver after their lead time (2 s ± 20% here), off the thread that received the order: placing
 * it returns right away and the order stays open until the delivery arrives.
 */
@QuarkusTest
@TestProfile(SupplierLeadTimeFlowTest.LeadTime.class)
class SupplierLeadTimeFlowTest {

    /** Also stops CashpointStub. */
    public static class LeadTime implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "supplier-stub.lead-time", "2s",
                "quarkus.scheduler.enabled", "false");
        }
    }

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject FruitsHandler fruitsHandler;
    @Inject NonFoodHandler nonFoodHandler;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/locations/dc/products").asString().equals("[]") && auditHelper.isEmpty();
        });
    }

    @Test
    void a_rest_supplier_delivers_after_its_lead_time() {
        assertDeliveredAfterLeadTime("Apple", () -> fruitsHandler.order(new FruitOrder("Apple", 10)));
    }

    @Test
    void a_kafka_supplier_delivers_after_its_lead_time() {
        assertDeliveredAfterLeadTime("Towel", () -> nonFoodHandler.order(new NonFoodOrder("Towel", 10)));
    }

    private void assertDeliveredAfterLeadTime(String productName, Runnable order) {
        long start = System.nanoTime();
        order.run();
        // the stub schedules the delivery instead of waiting for it; a waiting REST stub would block this call
        assertThat(elapsedSince(start)).isLessThan(Duration.ofSeconds(1));
        assertThat(dcAvailable(productName)).isZero();
        assertThat(openOrders()).isEqualTo(1);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(dcAvailable(productName)).isEqualTo(10);
            assertThat(openOrders()).isZero();
        });
        // the shortest jittered lead time
        assertThat(elapsedSince(start)).isGreaterThanOrEqualTo(Duration.ofMillis(1600));
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static int openOrders() {
        return given().get("/admin/supplier-orders-fragment").jsonPath().getList("vm.supplierOrders").size();
    }

    private static int dcAvailable(String productName) {
        Integer amount = given().get("/api/locations/" + Locations.DC.id() + "/products").jsonPath()
            .get("find { it.name == '" + productName + "' }.availableAmount");
        return amount == null ? 0 : amount;
    }
}
