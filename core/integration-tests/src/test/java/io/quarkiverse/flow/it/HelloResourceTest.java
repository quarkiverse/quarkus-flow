package io.quarkiverse.flow.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
public class HelloResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", is("hello world!"));
    }

    @Test
    public void testProblemDetails() {
        given()
                .when().get("/hello/problem-details")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("type", containsString("https://serverlessworkflow.io/spec/1.0.0/errors/communication"));
    }
}
