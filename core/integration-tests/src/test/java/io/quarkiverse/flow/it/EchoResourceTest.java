package io.quarkiverse.flow.it;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class EchoResourceTest {

    @Test
    void shouldExecuteUsingWorkflowFile() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Anakin Skywalker"));
    }

}
