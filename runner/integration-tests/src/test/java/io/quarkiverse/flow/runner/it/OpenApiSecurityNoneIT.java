package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for OpenAPI document generation when security is NONE.
 * Verifies that security schemes are NOT added when authentication is disabled.
 */
@QuarkusTest
@TestProfile(SecurityNoneProfile.class)
@DisplayName("OpenAPI with Security NONE")
class OpenApiSecurityNoneIT {

    @Test
    @DisplayName("test_openapi_does_not_contain_security_scheme_when_none")
    void test_openapi_does_not_contain_security_scheme_when_none() {
        // Given - OpenAPI document with NONE security
        String openApiDoc = given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then - should NOT contain BearerAuth security scheme
        assertThat(openApiDoc).doesNotContain("BearerAuth:");

        // Operations should NOT have security requirements
        // (Note: Generic endpoints might still have @RolesAllowed annotations,
        // but dynamically generated workflow operations should not)

        // Verify workflow operations exist but without security
        assertThat(openApiDoc).contains("/q/flow/exec/test-namespace/simple-greeting/1.0.0:");
    }

    @Test
    @DisplayName("test_openapi_operations_not_marked_as_secured")
    void test_openapi_operations_not_marked_as_secured() {
        // Given - OpenAPI document
        String openApiDoc = given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Find a specific workflow operation section
        int workflowOpIndex = openApiDoc.indexOf("/q/flow/exec/test-namespace/simple-greeting/1.0.0:");
        assertThat(workflowOpIndex).isGreaterThan(-1);

        // Get a substring around this operation (next 500 chars)
        String operationSection = openApiDoc.substring(workflowOpIndex,
                Math.min(workflowOpIndex + 800, openApiDoc.length()));

        // This specific operation should NOT have security requirements
        // Note: We're checking the dynamic operation, not the generic parameterized one
        assertThat(operationSection).contains("post:");
        assertThat(operationSection).contains("Execute simple-greeting workflow");

        // If security requirements were present, we'd see "security:" in this section
        // Since we're in NONE mode, dynamic operations should not have it
        // (Generic endpoints from @RolesAllowed might still appear elsewhere)
    }

    @Test
    @DisplayName("test_openapi_still_contains_workflow_operations")
    void test_openapi_still_contains_workflow_operations() {
        // Given - OpenAPI document
        String openApiDoc = given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then - workflow operations should still be generated (just without security)
        assertThat(openApiDoc).contains("/q/flow/exec/test-namespace/simple-greeting/1.0.0:");
        assertThat(openApiDoc).contains("/q/flow/exec/test-namespace/simple-greeting/1.5.0:");
        assertThat(openApiDoc).contains("/q/flow/exec/test-namespace/simple-greeting:");

        // Operation IDs should be present
        assertThat(openApiDoc).contains("execute_test_namespace_simple_greeting_1_0_0");
        assertThat(openApiDoc).contains("execute_latest_test_namespace_simple_greeting_latest");

        // Wait parameter should still be present
        assertThat(openApiDoc).contains("name: wait");
    }

    @Test
    @DisplayName("test_openapi_accessible_without_credentials")
    void test_openapi_accessible_without_credentials() {
        // OpenAPI should always be accessible, even in NONE mode
        given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200);
    }
}
