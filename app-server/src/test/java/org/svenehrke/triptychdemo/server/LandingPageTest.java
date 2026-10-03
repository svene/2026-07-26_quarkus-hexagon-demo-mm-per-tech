package org.svenehrke.triptychdemo.server;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class LandingPageTest {

    @Test
    void the_landing_page_links_the_three_entry_points() {
        var response = given().get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/html");
        assertThat(response.asString())
            .contains("<a href=\"/admin\">Admin</a>")
            .contains("<a href=\"/locations\">Locations</a>")
            .contains("<a href=\"/shop\">Shop</a>");
    }
}
