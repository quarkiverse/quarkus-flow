package io.quarkiverse.flow.it;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;

@QuarkusIntegrationTest
public class EchoResourceIT {

    @Test
    void inject_from_file_should_execute_using_Flow() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo/from-flow")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Anakin Skywalker"));
    }

    @Test
    void inject_from_file_should_execute_using_WorkflowDefinition() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo/from-workflow-def")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Anakin Skywalker"));
    }

}
