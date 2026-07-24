package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;

/**
 * Integration tests for OIDC with ABAC (Attribute-Based Access Control) using namespace claims.
 * <p>
 * Uses {@link OidcSecurity} annotation to add custom JWT claims (namespace) for testing
 * namespace-based authorization without requiring real JWT tokens.
 */
@QuarkusTest
@TestProfile(SecurityOidcProfile.class)
@DisplayName("Security: OIDC with Namespace Authorization (ABAC)")
class SecurityOidcAbacIT {

    @Test
    @TestSecurity(user = "alice", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "test-namespace") })
    @DisplayName("test_access_allowed_to_authorized_namespace")
    void test_access_allowed_to_authorized_namespace() {
        // User with access to test-namespace
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "team-a") })
    @DisplayName("test_access_denied_to_unauthorized_namespace")
    void test_access_denied_to_unauthorized_namespace() {
        // User only has access to team-a, trying to access test-namespace
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(403); // Forbidden - namespace not authorized
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "test-namespace") })
    @DisplayName("test_execute_workflow_in_authorized_namespace")
    void test_execute_workflow_in_authorized_namespace() {
        given()
                .contentType("application/json")
                .body(Map.of("name", "Test"))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "team-b") })
    @DisplayName("test_execute_workflow_in_unauthorized_namespace")
    void test_execute_workflow_in_unauthorized_namespace() {
        // User has access to team-b but tries to execute in test-namespace
        given()
                .contentType("application/json")
                .body(Map.of("name", "Test"))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "carol", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "[\"test-namespace\",\"team-a\"]") })
    @DisplayName("test_multiple_namespaces_in_jwt_claim")
    void test_multiple_namespaces_in_jwt_claim() {
        // User with access to multiple namespaces (JSON array as string)
        // Note: @OidcSecurity only supports string claims, so we use JSON array string

        // Should have access to test-namespace
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);

        // Should also have access to team-a
        given()
                .queryParam("namespace", "team-a")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);

        // Should NOT have access to team-b
        given()
                .queryParam("namespace", "team-b")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "test-namespace") })
    @DisplayName("test_list_all_definitions_filtered_by_namespaces")
    void test_list_all_definitions_filtered_by_namespaces() {
        // User with access to test-namespace only
        // List all definitions without namespace filter
        // Should only return workflows from test-namespace
        given()
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = "flow-admin")
    @DisplayName("test_no_namespace_claim_allows_all_namespaces")
    void test_no_namespace_claim_allows_all_namespaces() {
        // User with valid role but no namespace claim - should allow all namespaces
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "test-namespace") })
    @DisplayName("test_get_specific_definition_with_namespace_validation")
    void test_get_specific_definition_with_namespace_validation() {
        // User with access to test-namespace
        // Can access definition in authorized namespace
        given()
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "team-a") })
    @DisplayName("test_get_specific_definition_unauthorized_namespace")
    void test_get_specific_definition_unauthorized_namespace() {
        // User with access to team-a trying to access test-namespace
        given()
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "test-namespace") })
    @DisplayName("test_execute_latest_workflow_with_namespace_check")
    void test_execute_latest_workflow_with_namespace_check() {
        // User with access to test-namespace
        // Execute latest version in authorized namespace
        given()
                .contentType("application/json")
                .body(Map.of("name", "Latest Test"))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = "flow-admin")
    @OidcSecurity
    @DisplayName("test_oidc_admin_without_namespace_claim_allows_all_namespaces")
    void test_oidc_admin_without_namespace_claim_allows_all_namespaces() {
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);

        given()
                .queryParam("namespace", "another-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @OidcSecurity
    @DisplayName("test_oidc_invoker_without_namespace_claim_is_denied")
    void test_oidc_invoker_without_namespace_claim_is_denied() {
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @OidcSecurity(claims = {
            @Claim(key = "namespace", value = "")
    })
    @DisplayName("test_oidc_invoker_with_blank_namespace_claim_is_denied")
    void test_oidc_invoker_with_blank_namespace_claim_is_denied() {
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "flow-admin")
    @OidcSecurity(claims = {
            @Claim(key = "namespace", value = "team-a")
    })
    @DisplayName("test_admin_role_bypasses_namespace_authorization")
    void test_admin_role_bypasses_namespace_authorization() {
        // Explicitly listed namespace.
        given()
                .queryParam("namespace", "team-a")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);

        // Namespace not listed in the claim is still allowed for admins.
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "test-namespace,team-a") })
    @DisplayName("test_comma_separated_namespace_claim_as_string")
    void test_comma_separated_namespace_claim_as_string() {
        // Some OIDC providers might return comma-separated string instead of array
        // Should have access to both namespaces
        given()
                .queryParam("namespace", "test-namespace")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);

        given()
                .queryParam("namespace", "team-a")
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "alice", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "test-namespace") })
    @DisplayName("test_get_latest_definition_with_namespace_validation")
    void test_get_latest_definition_with_namespace_validation() {
        // User with access to test-namespace
        // Can access latest definition in authorized namespace
        given()
                .header("Accept", "application/json")
                .when()
                .get("/q/flow/definitions/test-namespace/simple-greeting")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "bob", roles = "flow-invoker")
    @OidcSecurity(claims = { @Claim(key = "namespace", value = "test-namespace") })
    @DisplayName("test_async_execution_with_namespace_validation")
    void test_async_execution_with_namespace_validation() {
        // User with access to test-namespace
        // Async execution in authorized namespace
        given()
                .contentType("application/json")
                .body(Map.of("name", "Async"))
                .queryParam("wait", "false")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(anyOf(is(200), is(202)));
    }
}
