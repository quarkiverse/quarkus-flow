package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * Integration tests for NONE security mode (development mode).
 * Uses {@link SecurityNoneProfile} to configure security.type=none.
 * In this mode, all requests are allowed without authentication.
 */
@QuarkusTest
@TestProfile(SecurityNoneProfile.class)
@DisplayName("Security: NONE (Development Mode)")
class SecurityNoneIT {

    @Test
    @DisplayName("test_list_definitions_without_auth_header")
    void test_list_definitions_without_auth_header() {
        // In NONE mode, no authentication required
        given()
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_get_specific_definition_without_auth")
    void test_get_specific_definition_without_auth() {
        given()
                .header("Accept", "application/json")
                .when()
                .get("/runner/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_execute_workflow_without_auth")
    void test_execute_workflow_without_auth() {
        Map<String, Object> input = Map.of("name", "No Auth Test");

        ExecutionResponse response = given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200)
                .extract()
                .as(ExecutionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.instanceId()).isNotBlank();
        assertThat(response.status()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    @DisplayName("test_execute_workflow_async_without_auth")
    void test_execute_workflow_async_without_auth() {
        Map<String, Object> input = Map.of("name", "Async No Auth");

        given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "false")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(202);
    }

    @Test
    @DisplayName("test_list_definitions_with_namespace_filter")
    void test_list_definitions_with_namespace_filter() {
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_all_endpoints_accessible_without_auth")
    void test_all_endpoints_accessible_without_auth() {
        // Verify all endpoints work without any authentication

        // List definitions
        given()
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);

        // Get specific definition (JSON)
        given()
                .header("Accept", "application/json")
                .when()
                .get("/runner/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);

        // Get specific definition (YAML)
        given()
                .header("Accept", "application/yaml")
                .when()
                .get("/runner/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);

        // Execute workflow (sync)
        given()
                .contentType("application/json")
                .body(Map.of("name", "Test"))
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);

        // Execute workflow (async)
        given()
                .contentType("application/json")
                .body(Map.of("name", "Test"))
                .queryParam("wait", "false")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(202);

        // Execute latest version
        given()
                .contentType("application/json")
                .body(Map.of("name", "Test"))
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_bearer_token_ignored_in_none_mode")
    void test_bearer_token_ignored_in_none_mode() {
        // Even if a Bearer token is provided, it should be ignored in NONE mode
        given()
                .header("Authorization", "Bearer some-random-token")
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_invalid_bearer_token_ignored_in_none_mode")
    void test_invalid_bearer_token_ignored_in_none_mode() {
        // Invalid tokens should also be ignored in NONE mode
        given()
                .header("Authorization", "Bearer invalid-garbage")
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }
}
