package com.example.hexdemo.server;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class BeverageOrderDeliveryFlowTest {

    @Inject
    TestInventoryHelper helper;

    @BeforeEach
    void setUp() {
        await().atMost(5, SECONDS).until(() -> {
            helper.resetInventory();
            return given().get("/api/products").asString().equals("[]");
        });
    }

    @Test
    void ordered_beverages_arrive_via_kafka_and_appear_in_inventory() {
        var orderResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "productName": "Cola",
                  "quantity": 12
                }
                """)
            .post("/api/products/order-beverages");
        assertThat(orderResponse.statusCode()).isEqualTo(204);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var response = given().get("/api/products");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("""
                [{"name":"Cola","type":"BEVERAGE","availableAmount":12}]""");
        });
    }
}
