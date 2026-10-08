package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogHandler;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class AuditLogReceiverTest {

    @Inject TestAuditLogHelper auditLogHelper;
    @Inject AuditLogHandler auditLogHandler;

    @BeforeEach
    void setUp() {
        auditLogHelper.clearAuditLog();
    }

    @Test
    void get_audit_log_serves_a_page_shell_that_polls_every_2s_without_sse() {
        var response = given().get("/audit-log");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("hx-get=\"/audit-log/page\"")
            .contains("hx-trigger=\"load, every 2s\"")
            .doesNotContain("refresh-audit-log")
            .doesNotContain("hx-sse");
    }

    @Test
    void page_view_has_no_entries_when_empty() {
        var response = given().get("/audit-log/page");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.jsonPath().getString("route")).isEqualTo("AuditLogPage");
        assertThat(response.jsonPath().getList("vm.auditEntries")).isEmpty();
        assertThat(response.jsonPath().getInt("vm.limit")).isEqualTo(300);
    }

    @Test
    void page_view_shows_recent_entries_after_an_order() {
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", 20)
            .post("/admin/order-fruits");

        await().atMost(5, SECONDS).until(() -> !auditLogHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_PLACED").isEmpty());
        // wait for the delivery (async via Kafka) too, or it may land in the next test's freshly reset inventory
        await().atMost(10, SECONDS).until(() -> given().get("/api/locations/dc/products").asString().contains("Banana"));

        var json = given().get("/audit-log/page").jsonPath();
        assertThat(json.getList("vm.auditEntries.event")).contains("FruitsHandler: FRUITS_ORDER_PLACED");
        assertThat(json.getList("vm.auditEntries.details", String.class)).anyMatch(d -> d.contains("Banana"));
    }

    @Test
    void page_view_shows_at_most_the_latest_300_entries() {
        IntStream.rangeClosed(1, 305).forEach(i -> auditLogHandler.log("TEST: ENTRY", "entry " + i));

        var details = given().get("/audit-log/page").jsonPath().getList("vm.auditEntries.details", String.class);

        // only the count: entries logged in a tight loop can share a timestamp, so their order isn't defined
        assertThat(details).hasSize(300);
    }
}
