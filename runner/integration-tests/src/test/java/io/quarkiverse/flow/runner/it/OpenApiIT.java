package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

/**
 * Integration tests for OpenAPI document generation.
 * Tests dynamic workflow operations and security scheme handling.
 */
@QuarkusTest
@TestProfile(SecurityApiKeyProfile.class)
@DisplayName("OpenAPI Document Generation")
class OpenApiIT {

    @Test
    @DisplayName("test_openapi_document_accessible_without_auth")
    void test_openapi_document_accessible_without_auth() {
        // OpenAPI document should be publicly accessible (no auth required)
        given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .contentType("application/yaml");
    }

    @Test
    @DisplayName("test_openapi_contains_dynamic_workflow_operations")
    void test_openapi_contains_dynamic_workflow_operations() {
        // Given - OpenAPI document
        String openApiDoc = given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then - should contain dynamically generated workflow operations

        // Specific version endpoints
        assertThat(openApiDoc).contains("/q/flow/exec/test-namespace/simple-greeting/1.0.0:");
        assertThat(openApiDoc).contains("/q/flow/exec/test-namespace/simple-greeting/1.5.0:");
        assertThat(openApiDoc).contains("/q/flow/exec/test-namespace/simple-greeting/2.0.0:");

        // Latest version endpoints
        assertThat(openApiDoc).contains("/q/flow/exec/test-namespace/simple-greeting:");

        // Operation IDs
        assertThat(openApiDoc).contains("execute_test_namespace_simple_greeting_1_0_0");
        assertThat(openApiDoc).contains("execute_latest_test_namespace_simple_greeting_latest");
    }

    @Test
    @DisplayName("test_openapi_contains_security_scheme_when_api_key_enabled")
    void test_openapi_contains_security_scheme_when_api_key_enabled() {
        // Given - OpenAPI document with API_KEY security
        String openApiDoc = given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then - should contain BearerAuth security scheme definition
        assertThat(openApiDoc).contains("securitySchemes:");
        assertThat(openApiDoc).contains("BearerAuth:");
        assertThat(openApiDoc).contains("type: http");
        assertThat(openApiDoc).contains("scheme: bearer");

        // Operations should reference security
        assertThat(openApiDoc).contains("- BearerAuth:");
        assertThat(openApiDoc).contains("- flow-admin");
        assertThat(openApiDoc).contains("- flow-invoker");
    }

    @Test
    @DisplayName("test_openapi_contains_wait_query_parameter")
    void test_openapi_contains_wait_query_parameter() {
        // Given - OpenAPI document
        String openApiDoc = given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then - should contain wait parameter definition
        assertThat(openApiDoc).contains("name: wait");
        assertThat(openApiDoc).contains("in: query");
        assertThat(openApiDoc).contains("type: boolean");
        assertThat(openApiDoc).containsIgnoringCase("Wait for workflow completion");
    }

    @Test
    @DisplayName("test_openapi_contains_response_codes")
    void test_openapi_contains_response_codes() {
        // Given - OpenAPI document
        String openApiDoc = given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then - should document all response codes (YAML uses double quotes)
        assertThat(openApiDoc).contains("\"200\":");
        assertThat(openApiDoc).contains("\"202\":");
        assertThat(openApiDoc).contains("\"401\":");
        assertThat(openApiDoc).contains("\"403\":");
        assertThat(openApiDoc).contains("\"404\":");
    }

    @Test
    @DisplayName("test_openapi_json_format_available")
    void test_openapi_json_format_available() {
        // OpenAPI should be available in JSON format too
        Response response = given()
                .header("Accept", "application/json")
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract()
                .response();

        // Verify it's valid JSON with expected structure
        assertThat(response.jsonPath().getString("openapi")).startsWith("3.");
        assertThat(response.jsonPath().getMap("paths")).isNotEmpty();
        assertThat(response.jsonPath().getMap("paths"))
                .containsKey("/q/flow/exec/test-namespace/simple-greeting/1.0.0");
    }

    @Test
    @DisplayName("test_swagger_ui_accessible")
    void test_swagger_ui_accessible() {
        // Swagger UI should be accessible
        given()
                .when()
                .get("/q/swagger-ui")
                .then()
                .statusCode(200)
                .contentType("text/html");
    }

    @Test
    @DisplayName("test_openapi_contains_definition_endpoints")
    void test_openapi_contains_definition_endpoints() {
        // Given - OpenAPI document
        String openApiDoc = given()
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        // Then - should contain definition resource endpoints
        assertThat(openApiDoc).contains("/q/flow/definitions:");
        assertThat(openApiDoc).contains("/q/flow/definitions/{namespace}/{name}:");
        assertThat(openApiDoc).contains("/q/flow/definitions/{namespace}/{name}/{version}:");
    }

    @Test
    @DisplayName("test_openapi_request_body_schema_parses_workflow_input_schema")
    void test_openapi_request_body_schema_parses_workflow_input_schema() {
        // Given - OpenAPI document in JSON format for easier parsing
        Response response = given()
                .header("Accept", "application/json")
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract()
                .response();

        // Then - request body schema should be based on workflow's input schema
        var requestBodySchema = response.jsonPath()
                .getMap("paths.'/q/flow/exec/test-namespace/simple-greeting/1.0.0'.post.requestBody.content.'application/json'.schema");

        assertThat(requestBodySchema).isNotNull();
        assertThat(requestBodySchema).containsKey("type");
    }

}
