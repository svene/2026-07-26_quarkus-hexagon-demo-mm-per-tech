package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.occupancy.OccupancyHandler;
import org.svenehrke.triptychdemo.cross.occupancy.OccupancyMessage;
import org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancy;
import org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancyHistoryTable;
import org.svenehrke.triptychdemo.cross.occupancy.StoreOccupancyTable;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The stores' occupancy reports (store-occupancy topic) on /locations, their history, and the automatic tills. */
@QuarkusTest
class StoreOccupancyFlowTest {

    @Inject TestAuditLogHelper auditHelper;
    @Inject TestOccupancyPublisher occupancyPublisher;
    @Inject StoreOccupancyTable occupancyTable;
    @Inject StoreOccupancyHistoryTable historyTable;
    @Inject OccupancyHandler occupancyHandler;

    /**
     * Waits until the receiver consumes: right after the start its consumer may not be assigned the topic yet and
     * would skip what is published before (auto.offset.reset=latest). Publishing a report again is harmless - only
     * the latest per store counts. Basel: a probe that arrives after the cleanup is older than any Basel report of a
     * test, and no test expects Basel without a report.
     */
    @BeforeEach
    void setUp() {
        await().atMost(30, SECONDS).untilAsserted(() -> {
            occupancyPublisher.publish(new OccupancyMessage("basel", Instant.now(), 1, 10, 0, 1, 0, 0, 0));
            assertThat(fragment("basel").getMap("vm.report")).isNotNull();
        });
        occupancyTable.deleteAll();
        historyTable.deleteAll();
        auditHelper.clearAuditLog();
    }

    @AfterEach
    void tearDown() {
        TestConfigOverrides.clear();
    }

    private static JsonPath fragment(String storeId) {
        return given().get("/locations/" + storeId + "/occupancy-fragment").jsonPath();
    }

    @Test
    void without_a_report_the_fragment_has_none() {
        var json = fragment("bern");

        assertThat(json.getString("route")).isEqualTo("StoreOccupancy");
        assertThat(json.getString("vm.storeId")).isEqualTo("bern");
        assertThat(json.getMap("vm.report")).isNull();
        assertThat(json.getBoolean("vm.autoTills")).isFalse();
    }

    @Test
    void a_report_shows_up_on_the_stores_fragment_and_the_page() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        occupancyPublisher.publish(new OccupancyMessage("zurich", now, 16, 16, 5, 4, 4, 15, 7));

        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(fragment("zurich").<Integer>get("vm.report.inside")).isEqualTo(16));
        var report = fragment("zurich");
        assertThat(report.getInt("vm.report.capacity")).isEqualTo(16);
        assertThat(report.getBoolean("vm.report.full")).isTrue();
        assertThat(report.getInt("vm.report.paid")).isEqualTo(15);
        assertThat(report.getInt("vm.report.turnedAway")).isEqualTo(7);
        assertThat(report.getInt("vm.report.queuing")).isEqualTo(5);
        assertThat(report.getInt("vm.report.tills")).isEqualTo(4);
        assertThat(report.getInt("vm.report.tillsBusy")).isEqualTo(4);
        assertThat(report.getBoolean("vm.report.stale")).isFalse();
        assertThat(fragment("bern").getMap("vm.report")).isNull();

        var page = given().get("/locations/page").jsonPath();
        assertThat(page.getList("vm.occupancy.storeId")).containsExactly("zurich", "bern", "basel");
        assertThat(page.getInt("vm.occupancy.find { it.storeId == 'zurich' }.report.inside")).isEqualTo(16);
        // valid reports are not audit-logged: every store reports every few seconds
        assertThat(auditHelper.isEmpty()).isTrue();
    }

    @Test
    void an_older_report_than_the_stored_one_is_ignored() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        occupancyPublisher.publish(new OccupancyMessage("basel", now, 10, 12, 2, 2, 2, 0, 0));
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(fragment("basel").<Integer>get("vm.report.inside")).isEqualTo(10));

        // overtaken (or redelivered): older than what is stored, then a newer one to know both were processed
        occupancyPublisher.publish(new OccupancyMessage("basel", now.minusSeconds(5), 9, 12, 0, 2, 0, 0, 0));
        occupancyPublisher.publish(new OccupancyMessage("basel", now, 8, 12, 0, 2, 0, 0, 0));
        occupancyPublisher.publish(new OccupancyMessage("basel", now.plusSeconds(5), 11, 12, 3, 2, 2, 0, 1));

        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(fragment("basel").<Integer>get("vm.report.inside")).isEqualTo(11));
        // only the newer ones are in the history
        assertThat(fragment("basel").getList("vm.metrics.points.inside")).containsExactly(10, 11);
    }

    @Test
    void the_reports_of_the_last_minutes_are_on_the_fragment_as_metrics() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        occupancyPublisher.publish(new OccupancyMessage("zurich", now.minusSeconds(10), 12, 16, 2, 4, 4, 14, 0));
        occupancyPublisher.publish(new OccupancyMessage("zurich", now.minusSeconds(5), 16, 16, 6, 4, 4, 16, 2));
        occupancyPublisher.publish(new OccupancyMessage("zurich", now, 16, 16, 8, 5, 5, 18, 4));

        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(fragment("zurich").getList("vm.metrics.points")).hasSize(3));
        var metrics = fragment("zurich");
        assertThat(metrics.getInt("vm.metrics.windowSeconds")).isEqualTo(600);
        assertThat(metrics.getList("vm.metrics.points.queuing")).containsExactly(2, 6, 8);
        assertThat(metrics.getList("vm.metrics.points.tills")).containsExactly(4, 4, 5);
        assertThat(metrics.getInt("vm.metrics.fullPercent")).isEqualTo(67);
        assertThat(metrics.getDouble("vm.metrics.avgPaid")).isEqualTo(16.0);
        assertThat(metrics.getDouble("vm.metrics.avgTurnedAway")).isEqualTo(2.0);
        assertThat(fragment("bern").getList("vm.metrics.points")).isEmpty();
    }

    @Test
    void the_history_drops_reports_older_than_the_retention() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        occupancyHandler.record(new StoreOccupancy(Locations.BERN, now.minus(Duration.ofMinutes(31)), 1, 6, 0, 1, 0, 2, 0));
        occupancyHandler.record(new StoreOccupancy(Locations.BERN, now.minus(Duration.ofMinutes(20)), 2, 6, 0, 1, 0, 3, 0));
        occupancyHandler.record(new StoreOccupancy(Locations.BERN, now, 3, 6, 0, 1, 1, 4, 0));

        // 31 min: dropped by the last report (retention 30 min); 20 min: kept, but outside the 10 min window
        assertThat(historyTable.count(Locations.BERN)).isEqualTo(2);
        assertThat(occupancyHandler.history(Locations.BERN)).extracting(StoreOccupancy::inside).containsExactly(3);
    }

    @Test
    void a_report_older_than_30_s_is_shown_as_stale() {
        occupancyPublisher.publish(new OccupancyMessage("bern", Instant.now().minusSeconds(60), 3, 6, 0, 1, 1, 0, 0));

        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(fragment("bern").getMap("vm.report")).isNotNull());
        assertThat(fragment("bern").getBoolean("vm.report.stale")).isTrue();
    }

    @Test
    void invalid_reports_are_logged_and_not_stored() {
        var now = Instant.now();
        occupancyPublisher.publish(new OccupancyMessage("paris", now, 1, 6, 0, 1, 0, 0, 0));
        occupancyPublisher.publish(new OccupancyMessage("online", now, 1, 6, 0, 1, 0, 0, 0));
        occupancyPublisher.publish(new OccupancyMessage("bern", now, -1, 0, 0, 0, 0, 0, 0));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("StoreOccupancyReceiver: INVALID")).containsExactlyInAnyOrder(
                "storeId 'paris' is not a store",
                "storeId 'online' is not a store",
                "bern: capacity must be greater than or equal to 1, inside must be greater than or equal to 0, tills must be greater than or equal to 1"));
        assertThat(fragment("bern").getMap("vm.report")).isNull();
    }

    @Test
    void with_auto_tills_a_full_store_opens_a_till_at_its_checkout_system() {
        TestConfigOverrides.set("inventory.auto-tills.enabled", "true");
        assertThat(fragment("bern").getBoolean("vm.autoTills")).isTrue();

        // full, the one till busy, two queuing: TillPolicy opens a second till
        occupancyPublisher.publish(new OccupancyMessage("bern", Instant.now(), 6, 6, 2, 1, 1, 0, 3));

        // logged once the checkout system (the stub's REST endpoint) accepted the change
        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(auditHelper.findEventDetails("AutoTillsHandler: TILLS_OPENED"))
                .containsExactly("bern: 1→2, queuing 2, tills busy 1, inside 6/6"));
    }

    @Test
    void only_stores_have_an_occupancy() {
        assertThat(given().get("/locations/online/occupancy-fragment").statusCode()).isEqualTo(404);
    }
}
