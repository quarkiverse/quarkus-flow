package io.quarkiverse.flow.persistence.jpa.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class HelloResourceIT {

    @Test
    void hello_returns_hello_world() {
        given()
                .when().post("/hello-flow")
                .then()
                .statusCode(200)
                .body("message", equalTo("hello world!"));
    }

    @Test
    void hello_returns_hello_world_async() {
        given()
                .when().post("/hello-flow/async")
                .then()
                .statusCode(202);
    }
}
