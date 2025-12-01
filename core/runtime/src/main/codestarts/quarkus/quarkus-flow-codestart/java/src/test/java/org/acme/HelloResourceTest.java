package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class HelloResourceTest {

    @Test
    void hello_returns_hello_world() {
        given()
                .when().get("/hello-flow")
                .then()
                .statusCode(200)
                .body("message", equalTo("hello world!"));
    }

}
