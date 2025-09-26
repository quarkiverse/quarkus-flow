package io.quarkiverse.flow.it;

import static io.restassured.RestAssured.given;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class HelloAgenticResourceTest {

    @Test
    public void testHelloEndpoint() {
        final String result = given()
                .when()
                .body("Hello World!")
                .post("/hello")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Assertions.assertThat(result).containsIgnoringCase("Hello World");
    }

    @Test
    public void testHelloEndpoint_EmptyBody() {
        final String result = given()
                .when()
                .body("")
                .post("/hello")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Assertions.assertThat(result).containsIgnoringCase("is empty");
    }
}
