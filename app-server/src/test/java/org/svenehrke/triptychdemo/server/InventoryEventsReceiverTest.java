package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.occupancy.OccupancyHandler;
import org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancy;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class InventoryEventsReceiverTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject
    InventoryRepositorySPI inventory;
    @Inject
    OccupancyHandler occupancyHandler;
    @TestHTTPResource("/inventory/events")
    URI eventsUri;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
    }

    /** Proves the stream is pushed, not buffered: the second event only exists after the checkout. */
    @Test
    void events_stream_sends_inventory_changed_on_connect_and_after_a_purchase() throws Exception {
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 10);
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(eventsUri).header("Accept", "text/event-stream").build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenApply(r -> { r.body().forEach(lines::add); return r; });

            assertThat(nextEvent(lines)).isEqualTo("inventoryChanged"); // on connect

            given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("productName", "Apple")
                .formParam("quantity", "1")
                .post("/shop/checkout")
                .then().statusCode(200);

            assertThat(nextEvent(lines)).isEqualTo("inventoryChanged");
            response.cancel(true);
        }
    }

    /** Every kind of InventoryEvent reaches the stream - here a request that only goes PENDING, no stock moves. */
    @Test
    void events_stream_sends_inventory_changed_after_a_replenishment_request() throws Exception {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(eventsUri).header("Accept", "text/event-stream").build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenApply(r -> { r.body().forEach(lines::add); return r; });

            assertThat(nextEvent(lines)).isEqualTo("inventoryChanged"); // on connect

            given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("productName", "Apple")
                .formParam("quantity", "3")
                .post("/locations/bern/requests")
                .then().statusCode(200);

            assertThat(nextEvent(lines)).isEqualTo("inventoryChanged");
            response.cancel(true);
        }
    }

    /** A newer occupancy report becomes an event named after its store: only that store's line re-fetches. */
    @Test
    void events_stream_sends_occupancy_changed_per_store() throws Exception {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(eventsUri).header("Accept", "text/event-stream").build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenApply(r -> { r.body().forEach(lines::add); return r; });

            assertThat(nextEvent(lines)).isEqualTo("inventoryChanged"); // on connect

            occupancyHandler.record(new StoreOccupancy(Locations.ZURICH, Instant.now(), 12, 16, 3, 4, 4, 0));

            assertThat(nextEvent(lines)).isEqualTo("occupancyChanged-zurich");
            response.cancel(true);
        }
    }

    private static String nextEvent(BlockingQueue<String> lines) throws InterruptedException {
        while (true) {
            var line = lines.poll(5, SECONDS);
            assertThat(line).as("SSE line within 5 s").isNotNull();
            if (line.startsWith("event:")) return line.substring("event:".length()).trim();
        }
    }
}
