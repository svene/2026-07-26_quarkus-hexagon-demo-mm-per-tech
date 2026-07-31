package com.example.hexademo.server;

import com.example.hexademo.core.domain.ProductType;
import com.example.hexademo.core.port.out.InventoryRepositorySPI;
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
    @Inject InventoryRepositorySPI inventory;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            inventoryHelper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
        auditLogHelper.clearAuditLog();
    }

    @Test
    void get_empty_inventory_shows_no_products_message() {
        var response = given().get("/admin");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString()).contains("No products in inventory yet.");
    }

    @Test
    void get_with_products_shows_them_in_html_table() {
        inventory.addAmount("Apple", ProductType.FRUIT, 10);
        inventory.addAmount("Cola", ProductType.BEVERAGE, 5);

        var response = given().get("/admin");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString())
            .contains("Apple", "FRUIT", "10")
            .contains("Cola", "BEVERAGE", "5");
    }

    @Test
    void order_fruits_form_post_redirects_to_admin() {
        var response = given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", 20)
            .redirects().follow(false)
            .post("/admin/order-fruits");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.header("Location")).contains("/admin");
    }

    @Test
    void audit_log_empty_shows_no_entries_message() {
        var response = given().get("/admin/audit");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString()).contains("No audit log entries yet.");
    }

    @Test
    void audit_log_shows_recent_entries_after_an_order() {
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("productName", "Banana")
            .formParam("quantity", 20)
            .post("/admin/order-fruits");

        await().atMost(5, SECONDS).until(() -> !auditLogHelper.findEventDetails("FruitsHandler: FRUITS_ORDER_PLACED").isEmpty());

        var response = given().get("/admin/audit");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).contains("FruitsHandler: FRUITS_ORDER_PLACED", "Banana");
    }
}
