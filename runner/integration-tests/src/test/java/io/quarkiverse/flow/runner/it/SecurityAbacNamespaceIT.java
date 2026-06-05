package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for ABAC (Attribute-Based Access Control) namespace authorization.
 * <p>
 * Tests that users can only access workflows in namespaces they are authorized for.
 * Uses {@link SecurityAbacNamespaceProfile} which configures:
 * <ul>
 * <li>admin-key: access to ALL namespaces (no restriction)</li>
 * <li>team-a-key: access to team-a namespace only</li>
 * <li>team-b-key: access to team-b namespace only</li>
 * <li>multi-key: access to team-a AND team-b namespaces</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(SecurityAbacNamespaceProfile.class)
@DisplayName("Security: ABAC Namespace Authorization")
class SecurityAbacNamespaceIT {

    private static final String ADMIN_KEY = "admin-all-namespaces";
    private static final String TEAM_A_KEY = "team-a-secret";
    private static final String TEAM_B_KEY = "team-b-secret";
    private static final String MULTI_KEY = "multi-namespace-secret";

    // ==================== Admin (All Namespaces) Tests ====================

    @Test
    @DisplayName("test_admin_can_access_team_a_namespace")
    void test_admin_can_access_team_a_namespace() {
        given()
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-a")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_admin_can_access_team_b_namespace")
    void test_admin_can_access_team_b_namespace() {
        given()
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-b")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_admin_can_access_test_namespace")
    void test_admin_can_access_test_namespace() {
        given()
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_admin_can_list_all_definitions_without_filter")
    void test_admin_can_list_all_definitions_without_filter() {
        // No namespace in query = allowed (resource filters by authorized namespaces)
        given()
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    // ==================== Team A Namespace Restriction Tests ====================

    @Test
    @DisplayName("test_team_a_key_can_access_team_a_namespace")
    void test_team_a_key_can_access_team_a_namespace() {
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-a")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_team_a_key_cannot_access_team_b_namespace")
    void test_team_a_key_cannot_access_team_b_namespace() {
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-b")
                .then()
                .statusCode(403); // Namespace authorization blocks access
    }

    @Test
    @DisplayName("test_team_a_key_cannot_access_test_namespace")
    void test_team_a_key_cannot_access_test_namespace() {
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("test_team_a_key_can_list_definitions_without_namespace_filter")
    void test_team_a_key_can_list_definitions_without_namespace_filter() {
        // No namespace in request = filter allows it (resource will filter by team-a)
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    // ==================== Team B Namespace Restriction Tests ====================

    @Test
    @DisplayName("test_team_b_key_can_access_team_b_namespace")
    void test_team_b_key_can_access_team_b_namespace() {
        given()
                .header("Authorization", "Bearer " + TEAM_B_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-b")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_team_b_key_cannot_access_team_a_namespace")
    void test_team_b_key_cannot_access_team_a_namespace() {
        given()
                .header("Authorization", "Bearer " + TEAM_B_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-a")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("test_team_b_key_cannot_access_test_namespace")
    void test_team_b_key_cannot_access_test_namespace() {
        given()
                .header("Authorization", "Bearer " + TEAM_B_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(403);
    }

    // ==================== Multi-Namespace Tests ====================

    @Test
    @DisplayName("test_multi_key_can_access_team_a_namespace")
    void test_multi_key_can_access_team_a_namespace() {
        given()
                .header("Authorization", "Bearer " + MULTI_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-a")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_multi_key_can_access_team_b_namespace")
    void test_multi_key_can_access_team_b_namespace() {
        given()
                .header("Authorization", "Bearer " + MULTI_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-b")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_multi_key_cannot_access_test_namespace")
    void test_multi_key_cannot_access_test_namespace() {
        given()
                .header("Authorization", "Bearer " + MULTI_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(403);
    }

    // ==================== Workflow Execution Tests ====================

    @Test
    @DisplayName("test_team_a_key_can_execute_workflow_in_team_a_namespace")
    void test_team_a_key_can_execute_workflow_in_team_a_namespace() {
        // Note: This will 404 if no workflow exists in team-a namespace,
        // but should NOT return 403 (which means namespace authorization works)
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .contentType("application/json")
                .body(Map.of("name", "Test"))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/team-a/some-workflow/1.0.0")
                .then()
                .statusCode(404); // Not 403 - namespace is allowed, workflow doesn't exist
    }

    @Test
    @DisplayName("test_team_a_key_cannot_execute_workflow_in_team_b_namespace")
    void test_team_a_key_cannot_execute_workflow_in_team_b_namespace() {
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .contentType("application/json")
                .body(Map.of("name", "Test"))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/team-b/some-workflow/1.0.0")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("test_admin_can_execute_workflow_in_test_namespace")
    void test_admin_can_execute_workflow_in_test_namespace() {
        given()
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .contentType("application/json")
                .body(Map.of("name", "Admin Test"))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_team_a_key_cannot_execute_workflow_in_test_namespace")
    void test_team_a_key_cannot_execute_workflow_in_test_namespace() {
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .contentType("application/json")
                .body(Map.of("name", "Test"))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(403);
    }

    // ==================== Case Sensitivity Tests ====================

    @Test
    @DisplayName("test_namespace_matching_is_case_sensitive")
    void test_namespace_matching_is_case_sensitive() {
        // team-a key has lowercase "team-a", uppercase should be denied
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .when()
                .get("/q/flow/definitions?namespace=TEAM-A")
                .then()
                .statusCode(403);
    }

    // ==================== Path vs Query Parameter Tests ====================

    @Test
    @DisplayName("test_namespace_in_path_parameter_is_validated")
    void test_namespace_in_path_parameter_is_validated() {
        // Path parameter takes precedence - team-a key accessing test-namespace via path
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("test_namespace_in_query_parameter_is_validated")
    void test_namespace_in_query_parameter_is_validated() {
        // Query parameter - team-a key accessing team-b via query
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .when()
                .get("/q/flow/definitions?namespace=team-b")
                .then()
                .statusCode(403);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("test_namespace_with_whitespace_is_trimmed_and_validated")
    void test_namespace_with_whitespace_is_trimmed_and_validated() {
        // Whitespace should be trimmed, so " team-a " should match "team-a"
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .when()
                .get("/q/flow/definitions?namespace= team-a ")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("test_empty_namespace_query_param_is_allowed")
    void test_empty_namespace_query_param_is_allowed() {
        // Empty namespace means no filtering - should be allowed
        given()
                .header("Authorization", "Bearer " + TEAM_A_KEY)
                .when()
                .get("/q/flow/definitions?namespace=")
                .then()
                .statusCode(200);
    }
}
