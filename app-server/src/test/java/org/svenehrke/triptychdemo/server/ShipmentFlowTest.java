package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;
import org.svenehrke.triptychdemo.cross.location.Location;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.replenishment.CarrierSPI;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentHandler;
import org.svenehrke.triptychdemo.cross.replenishment.Shipment;
import org.svenehrke.triptychdemo.cross.replenishment.ShipmentStatus;
import org.svenehrke.triptychdemo.cross.replenishment.StockRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * DC → location transfers ship: the stock leaves the DC right away and reaches the location when the carrier reports
 * the arrival. The carrier is mocked here, so nothing arrives by itself - the test reports the arrivals.
 */
@QuarkusTest
class ShipmentFlowTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditHelper;
    @Inject InventoryRepositorySPI inventory;
    @Inject ReplenishmentHandler replenishmentHandler;
    @InjectMock CarrierSPI carrier;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            auditHelper.clearAuditLog();
            return given().get("/api/locations/dc/products").asString().equals("[]") && auditHelper.isEmpty();
        });
    }

    @Test
    void a_shipment_is_in_transit_until_its_arrival_is_reported() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);

        replenishmentHandler.request(new StockRequest(Locations.BERN, "Apple", 4));

        var shipment = dispatched();
        assertThat(shipment.location()).isEqualTo(Locations.BERN);
        assertThat(shipment.quantity()).isEqualTo(4);
        assertThat(shipment.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(available(Locations.DC, "Apple")).isEqualTo(6);
        assertThat(available(Locations.BERN, "Apple")).isZero();
        assertThat(inTransit(Locations.BERN, "Apple")).isEqualTo(4);
        // the DC's part is done
        assertThat(given().get("/locations/bern/inventory-fragment").jsonPath().getList("vm.requests.status"))
            .containsExactly("FULFILLED");

        replenishmentHandler.receiveShipment(shipment.id());

        assertThat(available(Locations.BERN, "Apple")).isEqualTo(4);
        assertThat(inTransit(Locations.BERN, "Apple")).isZero();
        assertThat(auditHelper.findEventDetails("ReplenishmentHandler: SHIPMENT_ARRIVED"))
            .containsExactly("shipment " + shipment.id() + " dc → bern: Apple 4");
    }

    @Test
    void an_arrival_reported_twice_is_booked_once() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);
        replenishmentHandler.request(new StockRequest(Locations.BERN, "Apple", 4));
        long id = dispatched().id();

        replenishmentHandler.receiveShipment(id);
        replenishmentHandler.receiveShipment(id);

        assertThat(available(Locations.BERN, "Apple")).isEqualTo(4);
        assertThat(auditHelper.findEventDetails("ReplenishmentHandler: SHIPMENT_ARRIVAL_IGNORED"))
            .containsExactly("shipment " + id + ": not in transit (unknown or arrived already)");
    }

    @Test
    void an_arrival_of_an_unknown_shipment_changes_nothing() {
        replenishmentHandler.receiveShipment(999_999);

        assertThat(given().get("/api/locations/bern/products").asString()).isEqualTo("[]");
        assertThat(auditHelper.findEventDetails("ReplenishmentHandler: SHIPMENT_ARRIVAL_IGNORED")).hasSize(1);
    }

    @Test
    void goods_in_transit_count_towards_the_locations_position() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 100);

        // cold start of a store: min 17, max 47 - position 0 requests 47, which ships
        replenishmentHandler.replenishIfLow(Locations.BERN, List.of("Apple"));
        assertThat(inTransit(Locations.BERN, "Apple")).isEqualTo(47);
        // position 0 available + 47 in transit: not below min, so no second request
        replenishmentHandler.replenishIfLow(Locations.BERN, List.of("Apple"));

        assertThat(given().get("/locations/bern/inventory-fragment").jsonPath().getList("vm.requests")).hasSize(1);
        assertThat(available(Locations.DC, "Apple")).isEqualTo(53);
    }

    @Test
    void a_failing_carrier_fails_neither_the_request_nor_the_shipment_and_overdue_shipments_are_sent_again() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);
        doThrow(new IllegalStateException("carrier down")).when(carrier).dispatch(any());

        given().contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Apple").formParam("quantity", "4")
            .post("/locations/bern/requests").then().statusCode(200);

        assertThat(auditHelper.findEventDetails("ReplenishmentHandler: SHIPMENT_DISPATCH_FAILED")).singleElement()
            .asString().endsWith("dc → bern: Apple 4: java.lang.IllegalStateException: carrier down");
        assertThat(inTransit(Locations.BERN, "Apple")).isEqualTo(4);

        // not overdue yet
        replenishmentHandler.redispatchOverdue(Duration.ofMinutes(2));
        verify(carrier, times(1)).dispatch(any());

        replenishmentHandler.redispatchOverdue(Duration.ZERO);
        verify(carrier, times(2)).dispatch(any());
        assertThat(auditHelper.findEventDetails("ReplenishmentHandler: SHIPMENT_REDISPATCHED")).singleElement()
            .asString().endsWith("dc → bern: Apple 4");
    }

    @Test
    void arrived_shipments_are_not_sent_again() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);
        replenishmentHandler.request(new StockRequest(Locations.BERN, "Apple", 4));
        replenishmentHandler.receiveShipment(dispatched().id());

        replenishmentHandler.redispatchOverdue(Duration.ZERO);

        verify(carrier, times(1)).dispatch(any());
    }

    private Shipment dispatched() {
        var captor = ArgumentCaptor.forClass(Shipment.class);
        verify(carrier).dispatch(captor.capture());
        return captor.getValue();
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
