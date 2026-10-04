package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentHandler;
import org.svenehrke.triptychdemo.cross.replenishment.StockRequest;
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
 * The real round trip through Kafka and the carrier stub, with a transit time of 2 s ± 20%: the goods are in transit
 * for that long, then the location books them.
 */
@QuarkusTest
@TestProfile(ShipmentTransitFlowTest.TransitTime.class)
class ShipmentTransitFlowTest {

    /** Also stops CashpointStub. */
    public static class TransitTime implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "carrier-stub.transit-time", "2s",
                "quarkus.scheduler.enabled", "false");
        }
    }

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject InventoryRepositorySPI inventory;
    @Inject ReplenishmentHandler replenishmentHandler;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/locations/dc/products").asString().equals("[]") && auditHelper.isEmpty();
        });
    }

    @Test
    void a_shipment_arrives_after_the_transit_time() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 50);

        long start = System.nanoTime();
        replenishmentHandler.request(new StockRequest(Locations.ZURICH, "Apple", 47));

        assertThat(available(Locations.DC, "Apple")).isEqualTo(3);
        assertThat(available(Locations.ZURICH, "Apple")).isZero();
        assertThat(inTransit(Locations.ZURICH, "Apple")).isEqualTo(47);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(available(Locations.ZURICH, "Apple")).isEqualTo(47);
            assertThat(inTransit(Locations.ZURICH, "Apple")).isZero();
        });
        // the shortest jittered transit time
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(1600));
    }

    private static int available(Location location, String productName) {
        Integer amount = given().get("/api/locations/" + location.id() + "/products").jsonPath()
            .get("find { it.name == '" + productName + "' }.availableAmount");
        return amount == null ? 0 : amount;
    }

    private static int inTransit(Location location, String productName) {
        Integer amount = given().get("/locations/" + location.id() + "/inventory-fragment").jsonPath()
            .get("vm.products.find { it.name == '" + productName + "' }.inTransit");
        return amount == null ? 0 : amount;
    }
}
