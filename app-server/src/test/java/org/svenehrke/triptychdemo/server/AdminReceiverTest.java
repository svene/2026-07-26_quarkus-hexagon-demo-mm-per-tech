package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;

import org.svenehrke.triptychdemo.cross.products.ProductType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class AdminReceiverTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditLogHelper;
    @Inject
    InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
        auditLogHelper.clearAuditLog();
    }

    @Test
    void get_admin_serves_the_page_shell_that_loads_the_page_view() {
        var response = given().get("/admin");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("<script src=\"/js/hono/hx-hono.js\">")
            .contains("hx-get=\"/admin/page\"");
    }

    @Test
    void page_view_with_empty_inventory_has_no_products() {
        var response = given().get("/admin/page");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.jsonPath().getString("route")).isEqualTo("AdminPage");
        assertThat(response.jsonPath().getList("vm.products")).isEmpty();
    }

    @Test
    void page_view_lists_products() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Cola", ProductType.BEVERAGE, 5);

        var json = given().get("/admin/page").jsonPath();

        assertThat(json.getList("vm.products.name")).containsExactlyInAnyOrder("Apple", "Cola");
        assertThat(json.getString("vm.products.find { it.name == 'Apple' }.type")).isEqualTo("FRUIT");
        assertThat(json.getInt("vm.products.find { it.name == 'Apple' }.availableAmount")).isEqualTo(10);
    }

    @Test
    void order_fruits_returns_200_with_empty_body() {
        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", 20)
            .post("/admin/order-fruits");

        // not 204: htmx never swaps a 204, and the empty swap is what clears a previous error below the form
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEmpty();
        assertThat(auditLogHelper.findEventDetails("AdminReceiver: FRUITS_ORDER_RECEIVED"))
            .containsExactly("Banana qty=20");
    }

    @Test
    void order_fruits_with_invalid_quantity_returns_400() {
        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", 0)
            .post("/admin/order-fruits");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("route")).isEqualTo("OrderErrors");
        assertThat(response.jsonPath().getList("vm.messages")).contains("must be greater than or equal to 1");
    }

    @Test
    void order_fruits_with_non_numeric_quantity_returns_400() {
        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", "abc")
            .post("/admin/order-fruits");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("route")).isEqualTo("OrderErrors");
        assertThat(response.jsonPath().getList("vm.messages")).contains("must be a number");
        // The receipt is logged with the raw input, before validation.
        assertThat(auditLogHelper.findEventDetails("AdminReceiver: FRUITS_ORDER_RECEIVED"))
            .containsExactly("Banana qty=abc");
    }

    @Test
    void order_fruits_with_blank_quantity_returns_400() {
        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", "")
            .post("/admin/order-fruits");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("route")).isEqualTo("OrderErrors");
        assertThat(response.jsonPath().getList("vm.messages")).contains("must not be blank");
    }

    @Test
    void page_view_has_no_audit_entries_when_empty() {
        var json = given().get("/admin/page").jsonPath();

        assertThat(json.getList("vm.auditEntries")).isEmpty();
    }

    @Test
    void audit_fragment_has_no_entries_when_empty() {
        var response = given().get("/admin/audit-fragment");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.jsonPath().getString("route")).isEqualTo("AuditPanel");
        assertThat(response.jsonPath().getList("vm.auditEntries")).isEmpty();
    }

    @Test
    void admin_page_and_audit_fragment_show_recent_entries_after_an_order() {
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", 20)
            .post("/admin/order-fruits");

        await().atMost(5, SECONDS).until(() -> !auditLogHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_PLACED").isEmpty());

        var pageEntries = given().get("/admin/page").jsonPath().getList("vm.auditEntries.event");
        assertThat(pageEntries).contains("FruitsHandler: FRUITS_ORDER_PLACED");

        var fragment = given().get("/admin/audit-fragment").jsonPath();
        assertThat(fragment.getList("vm.auditEntries.event")).contains("FruitsHandler: FRUITS_ORDER_PLACED");
        assertThat(fragment.getList("vm.auditEntries.details", String.class)).anyMatch(d -> d.contains("Banana"));
    }

    @Test
    void inventory_fragment_reflects_current_stock() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);

        var response = given().get("/admin/inventory-fragment");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("route")).isEqualTo("AdminInventory");
        assertThat(response.jsonPath().getList("vm.products.name")).containsExactly("Apple");
        assertThat(response.jsonPath().getInt("vm.products[0].availableAmount")).isEqualTo(10);
    }

    @Test
    void order_fruits_above_limit_returns_400() {
        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", "2001")
            .post("/admin/order-fruits");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getList("vm.messages")).contains("must be less than or equal to 2000");
    }
}
