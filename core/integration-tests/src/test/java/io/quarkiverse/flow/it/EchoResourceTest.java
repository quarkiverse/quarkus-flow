package io.quarkiverse.flow.it;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class EchoResourceTest {

    @Test
    void inject_from_file_should_execute_using_Flow() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo/from-flow")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Echo from test: Anakin Skywalker"));
    }

    @Test
    void inject_from_file_should_execute_using_WorkflowDefinition() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo/from-workflow-def")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Echo from test: Anakin Skywalker"));
    }

    @Test
    void inject_from_file_should_execute_using_Flow_v2() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo/v2/from-flow")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Echo (v0.2.0): Anakin Skywalker"));
    }

    @Test
    void inject_from_file_should_execute_using_WorkflowDefinition_v2() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo/v2/from-workflow-def")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Echo (v0.2.0): Anakin Skywalker"));
    }

    @Test
    @DisplayName("Versionless identifier should resolve WorkflowDefinition without version")
    void versionless_identifier_should_resolve_WorkflowDefinition_without_version() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo/from-versionless-workflow-def")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Echo (v0.2.0): Anakin Skywalker"));
    }

    @Test
    @DisplayName("Versionless identifier should resolve Flow without version")
    void versionless_identifier_should_resolve_Flow_without_version() {
        RestAssured.given()
                .queryParam("name", "Anakin Skywalker")
                .get("/echo/from-versionless-flow")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Echo (v0.2.0): Anakin Skywalker"));
    }

}
