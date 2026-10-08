package org.svenehrke.triptychdemo.server;

import org.svenehrke.triptychdemo.cross.inventory.InventoryRepositorySPI;

import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.svenehrke.triptychdemo.cross.purchase.Purchase;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseItem;
import org.svenehrke.triptychdemo.cross.purchase.PurchaseRepositorySPI;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderOrigin;
import org.svenehrke.triptychdemo.cross.purchasing.SupplierOrderRepositorySPI;
import org.svenehrke.triptychdemo.cross.replenishment.ReplenishmentRepositorySPI;
import org.svenehrke.triptychdemo.cross.replenishment.StockRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class AdminReceiverTest {

    @Inject TestInventoryHelper inventoryHelper;
    @Inject TestAuditLogHelper auditLogHelper;
    @Inject PurchaseRepositorySPI purchases;
    @Inject
    InventoryRepositorySPI inventory;
    @Inject
    ReplenishmentRepositorySPI replenishment;
    @Inject
    SupplierOrderRepositorySPI supplierOrders;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
        auditLogHelper.clearAuditLog();
    }

    @Test
    void get_admin_serves_the_page_shell_that_loads_the_page_view_without_a_nav() {
        var response = given().get("/admin");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("<script src=\"/js/hono/hx-hono.js\">")
            .contains("hx-get=\"/admin/page\"")
            .doesNotContain("location-nav");
    }

    @Test
    void page_view_with_empty_inventory_has_no_products() {
        var response = given().get("/admin/page");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.jsonPath().getString("route")).isEqualTo("AdminPage");
        assertThat(response.jsonPath().getList("vm.products")).isEmpty();
        // the order forms' products, from the catalog
        assertThat(response.jsonPath().getList("vm.catalog")).hasSize(28);
        assertThat(response.jsonPath().getString("vm.catalog[0].name")).isEqualTo("Mango");
        assertThat(response.jsonPath().getString("vm.catalog[0].type")).isEqualTo("FRUIT");
    }

    @Test
    void page_view_lists_products() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.DC, "Cola", ProductType.BEVERAGE, 5);

        var json = given().get("/admin/page").jsonPath();

        assertThat(json.getList("vm.products.name")).containsExactlyInAnyOrder("Apple", "Cola");
        assertThat(json.getString("vm.products.find { it.name == 'Apple' }.type")).isEqualTo("FRUIT");
        assertThat(json.getList("vm.products.find { it.name == 'Apple' }.amounts")).containsExactly(10, 0, 0, 0, 0);
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
        // wait for the delivery (async via Kafka), or it may land in the next test's freshly reset inventory
        await().atMost(10, SECONDS).until(() -> given().get("/api/locations/dc/products").asString().contains("Banana"));
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
    void inventory_fragment_is_a_product_by_location_matrix_with_the_dc_first() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);
        inventory.addAmount(Locations.BERN, "Apple", ProductType.FRUIT, 3);
        inventory.addAmount(Locations.ONLINE, "Apple", ProductType.FRUIT, 2);

        var response = given().get("/admin/inventory-fragment");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("route")).isEqualTo("AdminInventory");
        assertThat(response.jsonPath().getList("vm.locations.id")).containsExactly("dc", "zurich", "bern", "basel", "online");
        assertThat(response.jsonPath().getList("vm.products.name")).containsExactly("Apple");
        assertThat(response.jsonPath().getList("vm.products[0].amounts")).containsExactly(10, 0, 3, 0, 2);
    }

    @Test
    void inventory_fragment_lists_products_sorted_by_name_ignoring_case() {
        inventory.addAmount(Locations.DC, "milk", ProductType.DAIRY, 3);
        inventory.addAmount(Locations.DC, "Banana", ProductType.FRUIT, 5);
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 10);

        assertThat(given().get("/admin/inventory-fragment").jsonPath().getList("vm.products.name"))
            .containsExactly("Apple", "Banana", "milk");
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

    @Test
    void reset_deletes_the_inventory_the_requests_the_supplier_orders_the_purchases_and_the_audit_log() {
        inventory.addAmount(Locations.DC, "Apple", ProductType.FRUIT, 0);
        inventory.addAmount(Locations.BERN, "Cola", ProductType.BEVERAGE, 5);
        // the DC has no Apples, so the request stays pending
        replenishment.request(new StockRequest(Locations.ZURICH, "Apple", 4));
        supplierOrders.open("Apple", ProductType.FRUIT, 20, SupplierOrderOrigin.MANUAL);
        given().post("/admin/requests/0/reject"); // audit-logs REJECT_RECEIVED (409, there is no request 0)
        purchases.append(Locations.BERN, new Purchase(List.of(new PurchaseItem("Cola", 1))), Instant.now(), Instant.EPOCH);
        var before = given().get("/admin/page").jsonPath();
        assertThat(before.getList("vm.products")).hasSize(2);
        assertThat(before.getList("vm.pendingRequests")).hasSize(1);
        assertThat(before.getList("vm.supplierOrders")).hasSize(1);
        assertThat(given().get("/audit-log/page").jsonPath().getList("vm.auditEntries")).isNotEmpty();

        var response = given().post("/admin/reset");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEmpty();
        var json = given().get("/admin/page").jsonPath();
        assertThat(json.getList("vm.products")).isEmpty();
        assertThat(json.getList("vm.pendingRequests")).isEmpty();
        assertThat(json.getList("vm.supplierOrders")).isEmpty();
        assertThat(purchases.findRecent(Locations.BERN, 10)).isEmpty();
        assertThat(given().get("/audit-log/page").jsonPath().getList("vm.auditEntries.event"))
            .containsExactly("ResetHandler: INVENTORY_RESET");
    }
}
