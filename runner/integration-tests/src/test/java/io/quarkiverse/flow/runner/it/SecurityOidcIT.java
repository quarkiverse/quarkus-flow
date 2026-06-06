package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * Integration tests for OIDC authentication using @TestSecurity.
 * <p>
 * Uses {@link TestSecurity} annotation to mock OIDC authentication without
 * requiring real JWT tokens or a running OIDC server. This is the recommended
 * approach for testing OIDC-protected endpoints in Quarkus.
 */
@QuarkusTest
@TestProfile(SecurityOidcProfile.class)
@DisplayName("Security: OIDC Authentication")
class SecurityOidcIT {

    @Test
    @DisplayName("test_access_denied_without_authentication")
    void test_access_denied_without_authentication() {
        // No @TestSecurity annotation - should get 401
        given()
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-admin")
    @DisplayName("test_list_definitions_with_admin_role")
    void test_list_definitions_with_admin_role() {
        // User with flow-admin role
        given()
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @DisplayName("test_list_definitions_with_invoker_role")
    void test_list_definitions_with_invoker_role() {
        // User with flow-invoker role
        given()
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "carol", roles = "other-role")
    @DisplayName("test_access_denied_without_required_role")
    void test_access_denied_without_required_role() {
        // User with no flow-admin or flow-invoker role
        given()
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(403); // Forbidden - valid token but missing required role
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-admin")
    @DisplayName("test_execute_workflow_with_admin_role")
    void test_execute_workflow_with_admin_role() {
        Map<String, Object> input = Map.of("name", "OIDC Test");

        ExecutionResponse response = given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200)
                .extract()
                .as(ExecutionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.instanceId()).isNotBlank();
        assertThat(response.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(response.workflowOutput()).isNotEmpty();
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @DisplayName("test_execute_workflow_with_invoker_role")
    void test_execute_workflow_with_invoker_role() {
        Map<String, Object> input = Map.of("name", "OIDC Invoker");

        ExecutionResponse response = given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200)
                .extract()
                .as(ExecutionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-admin")
    @DisplayName("test_get_definition_json_with_valid_token")
    void test_get_definition_json_with_valid_token() {
        given()
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @DisplayName("test_get_definition_yaml_with_valid_token")
    void test_get_definition_yaml_with_valid_token() {
        given()
                .header("Accept", "application/yaml")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200)
                .contentType("application/yaml");
    }

    @Test
    @TestSecurity(user = "admin-invoker", roles = { "flow-admin", "flow-invoker" })
    @DisplayName("test_multiple_roles_in_token")
    void test_multiple_roles_in_token() {
        // User with both flow-admin and flow-invoker roles
        given()
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_openapi_accessible_without_token")
    void test_openapi_accessible_without_token() {
        // OpenAPI document should be publicly accessible even with OIDC enabled
        given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_swagger_ui_accessible_without_token")
    void test_swagger_ui_accessible_without_token() {
        // Swagger UI should be publicly accessible
        given()
                .when()
                .get("/q/swagger-ui")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "no-roles")
    @DisplayName("test_user_with_no_roles")
    void test_user_with_no_roles() {
        // User authenticated but no groups claim - should be denied
        given()
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-invoker")
    @DisplayName("test_execute_latest_workflow_version")
    void test_execute_latest_workflow_version() {
        Map<String, Object> input = Map.of("name", "Latest Version");

        given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-admin")
    @DisplayName("test_get_latest_definition")
    void test_get_latest_definition() {
        given()
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting")
                .then()
                .statusCode(200)
                .contentType("application/json");
    }
}
