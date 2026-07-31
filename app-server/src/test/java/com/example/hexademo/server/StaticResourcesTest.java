package com.example.hexademo.server;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class StaticResourcesTest {

    @Test
    void bulma_css_is_served() {
        var response = given().get("/css/bulma.min.css");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("text/css");
    }

    @Test
    void htmx_js_is_served() {
        var response = given().get("/js/htmx.org/2.0.8/htmx.js");

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
