package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GreetingRunnerTest {

    private static final String API_KEY = "demo-secret-change-me";

    @Test
    void testWorkflowExecution() {
        given()
                .header("Authorization", "Bearer " + API_KEY)
                .contentType("application/json")
                .body("{\"name\": \"World\"}")
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/examples/greeting/1.0.0")
                .then()
                .statusCode(200)
                .body("instanceId", notNullValue())
                .body("status", is("COMPLETED"))
                .body("workflowOutput.message", is("Hello, World!"));
    }

    @Test
    void testWorkflowExecutionLatestVersion() {
        given()
                .header("Authorization", "Bearer " + API_KEY)
                .contentType("application/json")
                .body("{\"name\": \"Runner\"}")
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/examples/greeting")
                .then()
                .statusCode(200)
                .body("workflowOutput.message", is("Hello, Runner!"));
    }

    @Test
    void testUnauthorizedAccess() {
        given()
                .contentType("application/json")
                .body("{\"name\": \"World\"}")
                .when()
                .post("/q/flow/exec/examples/greeting/1.0.0")
                .then()
                .statusCode(401);
    }

    @Test
    void testInvalidApiKey() {
        given()
                .header("Authorization", "Bearer wrong-key")
                .contentType("application/json")
                .body("{\"name\": \"World\"}")
                .when()
                .post("/q/flow/exec/examples/greeting/1.0.0")
                .then()
                .statusCode(401);
    }

    @Test
    void testListDefinitions() {
        given()
                .header("Authorization", "Bearer " + API_KEY)
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200)
                .body("[0].namespace", is("examples"))
                .body("[0].name", is("greeting"))
                .body("[0].version", is("1.0.0"));
    }

    @Test
    void testGetSpecificDefinition() {
        given()
                .header("Authorization", "Bearer " + API_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/examples/greeting/1.0.0")
                .then()
                .statusCode(200)
                .contentType("application/json");
    }
}
