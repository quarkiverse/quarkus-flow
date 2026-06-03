package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for API Key authentication.
 * Uses {@link SecurityApiKeyProfile} to configure API_KEY security mode.
 */
@QuarkusTest
@TestProfile(SecurityApiKeyProfile.class)
@DisplayName("Security: API Key Authentication")
class SecurityApiKeyIT {

    private static final String ADMIN_API_KEY = "test-admin-secret-key-123";
    private static final String INVOKER_API_KEY = "test-invoker-secret-key-456";
    private static final String INVALID_API_KEY = "wrong-key";

    @Test
    @DisplayName("test_list_definitions_with_valid_admin_key")
    void test_list_definitions_with_valid_admin_key() {
        given()
                .header("Authorization", "Bearer " + ADMIN_API_KEY)
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_list_definitions_with_valid_invoker_key")
    void test_list_definitions_with_valid_invoker_key() {
        given()
                .header("Authorization", "Bearer " + INVOKER_API_KEY)
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_list_definitions_without_auth_header")
    void test_list_definitions_without_auth_header() {
        given()
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("test_list_definitions_with_invalid_api_key")
    void test_list_definitions_with_invalid_api_key() {
        given()
                .header("Authorization", "Bearer " + INVALID_API_KEY)
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("test_list_definitions_with_empty_bearer_token")
    void test_list_definitions_with_empty_bearer_token() {
        given()
                .header("Authorization", "Bearer ")
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("test_list_definitions_with_wrong_auth_scheme")
    void test_list_definitions_with_wrong_auth_scheme() {
        given()
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("test_list_definitions_with_malformed_bearer_token")
    void test_list_definitions_with_malformed_bearer_token() {
        given()
                .header("Authorization", "BearerNOSPACE" + ADMIN_API_KEY)
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("test_list_definitions_case_insensitive_bearer")
    void test_list_definitions_case_insensitive_bearer() {
        // "bearer" lowercase should work
        given()
                .header("Authorization", "bearer " + ADMIN_API_KEY)
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_list_definitions_mixed_case_bearer")
    void test_list_definitions_mixed_case_bearer() {
        // "BeArEr" mixed case should work
        given()
                .header("Authorization", "BeArEr " + ADMIN_API_KEY)
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_get_specific_definition_with_valid_key")
    void test_get_specific_definition_with_valid_key() {
        given()
                .header("Authorization", "Bearer " + ADMIN_API_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/runner/definitions/test-namespace/simple-greeting/1.0.0")
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
                .statusCode(401);
    }

    @Test
    @DisplayName("test_execute_workflow_with_valid_admin_key")
    void test_execute_workflow_with_valid_admin_key() {
        Map<String, Object> input = Map.of("name", "Admin Test");

        given()
                .header("Authorization", "Bearer " + ADMIN_API_KEY)
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_execute_workflow_with_valid_invoker_key")
    void test_execute_workflow_with_valid_invoker_key() {
        Map<String, Object> input = Map.of("name", "Invoker Test");

        given()
                .header("Authorization", "Bearer " + INVOKER_API_KEY)
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_execute_workflow_without_auth")
    void test_execute_workflow_without_auth() {
        Map<String, Object> input = Map.of("name", "Test");

        given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("test_execute_workflow_with_invalid_key")
    void test_execute_workflow_with_invalid_key() {
        Map<String, Object> input = Map.of("name", "Test");

        given()
                .header("Authorization", "Bearer " + INVALID_API_KEY)
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("test_execute_workflow_async_with_valid_key")
    void test_execute_workflow_async_with_valid_key() {
        Map<String, Object> input = Map.of("name", "Async Test");

        given()
                .header("Authorization", "Bearer " + ADMIN_API_KEY)
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "false")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(202);
    }

    @Test
    @DisplayName("test_api_key_with_extra_whitespace_is_trimmed")
    void test_api_key_with_extra_whitespace_is_trimmed() {
        // API key with leading/trailing spaces should be trimmed and work
        given()
                .header("Authorization", "Bearer   " + ADMIN_API_KEY + "   ")
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_verify_admin_can_access_all_endpoints")
    void test_verify_admin_can_access_all_endpoints() {
        // Admin should have full access (flow-admin role)

        // List definitions
        given()
                .header("Authorization", "Bearer " + ADMIN_API_KEY)
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);

        // Get specific definition
        given()
                .header("Authorization", "Bearer " + ADMIN_API_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/runner/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);

        // Execute workflow
        given()
                .header("Authorization", "Bearer " + ADMIN_API_KEY)
                .contentType("application/json")
                .body(Map.of("name", "Admin"))
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_verify_invoker_can_access_all_current_endpoints")
    void test_verify_invoker_can_access_all_current_endpoints() {
        // Invoker should have access to all current endpoints (flow-invoker role)
        // Note: In future, if we add definition CRUD (POST/PUT/DELETE), invoker may be restricted

        // List definitions - allowed
        given()
                .header("Authorization", "Bearer " + INVOKER_API_KEY)
                .when()
                .get("/runner/definitions")
                .then()
                .statusCode(200);

        // Get specific definition - allowed
        given()
                .header("Authorization", "Bearer " + INVOKER_API_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/runner/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);

        // Execute workflow - allowed
        given()
                .header("Authorization", "Bearer " + INVOKER_API_KEY)
                .contentType("application/json")
                .body(Map.of("name", "Invoker"))
                .queryParam("wait", "true")
                .when()
                .post("/runner/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }
}
