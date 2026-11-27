package io.quarkiverse.flow.runner.security;

import static io.restassured.RestAssured.given;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;

/**
 * Verifies that the {@code flow-service-reader} role grants read-only access to the runner
 * read endpoints (definitions, instances, discovery) while being denied on execution and
 * lifecycle (mutating) endpoints.
 */
@QuarkusTest
@TestProfile(ReaderRoleEnforcementTest.NoneSecurityProfile.class)
// Windows CI runners have no Docker; see the module's test application.properties
@DisabledOnOs(OS.WINDOWS)
@DisplayName("Reader Role Enforcement")
class ReaderRoleEnforcementTest {

    public static class NoneSecurityProfile implements io.quarkus.test.junit.QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>();
            config.put("quarkus.flow.runner.security.type", "none");
            // This test covers RBAC (role enforcement) only; namespace ABAC is covered by the OIDC ITs.
            config.put("quarkus.flow.runner.security.namespace.validate", "false");
            config.put("quarkus.http.test-port", "0");
            return config;
        }

        @Override
        public String getConfigProfile() {
            return "reader-role-none";
        }
    }

    @Test
    @TestSecurity(user = "reader", roles = AuthzConsts.ROLE_SERVICE_READER)
    @DisplayName("test_reader_role_can_list_definitions")
    void test_reader_role_can_list_definitions() {
        given()
                .when()
                .get("/q/flow/definitions")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "reader", roles = AuthzConsts.ROLE_SERVICE_READER)
    @DisplayName("test_reader_role_can_list_instances")
    void test_reader_role_can_list_instances() {
        given()
                .when()
                .get("/q/flow/instances")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "reader", roles = AuthzConsts.ROLE_SERVICE_READER)
    @DisplayName("test_reader_role_can_access_discovery_endpoint")
    void test_reader_role_can_access_discovery_endpoint() {
        // The instance does not exist, but the role check must pass (404, not 403)
        given()
                .when()
                .get("/q/flow/unknown-ns/unknown-wf/1.0.0/discovery/unknown-instance")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "reader", roles = AuthzConsts.ROLE_SERVICE_READER)
    @DisplayName("test_reader_role_cannot_execute_workflow")
    void test_reader_role_cannot_execute_workflow() {
        given()
                .contentType("application/json")
                .body(Map.of("name", "reader"))
                .when()
                .post("/q/flow/exec/test-namespace/test-workflow/1.0.0/sync")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "reader", roles = AuthzConsts.ROLE_SERVICE_READER)
    @DisplayName("test_reader_role_cannot_cancel_workflow_instance")
    void test_reader_role_cannot_cancel_workflow_instance() {
        given()
                .contentType("application/json")
                .when()
                .post("/q/flow/exec/test-namespace/test-workflow/1.0.0/some-instance-id/cancel")
                .then()
                .statusCode(403);
    }
}
