package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowStatus;

@SuppressWarnings("unchecked")
@QuarkusTest
@TestProfile(DefaultTestProfile.class)
@DisplayName("Runner Execution Resource Integration Tests")
class RunnerExecResourceIT {

    @Test
    @DisplayName("test_execute_workflow_sync_with_specific_version")
    void test_execute_workflow_sync_with_specific_version() {
        // Given
        Map<String, Object> input = Map.of("name", "Quarkus");

        // When
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

        // Then
        assertThat(response).isNotNull();
        assertThat(response.instanceId()).isNotBlank();
        assertThat(response.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(response.workflowOutput()).isNotEmpty();
        assertThat(response.workflowOutput()).containsEntry("greeting", "Quarkus says hello!");
    }

    @Test
    @DisplayName("test_execute_workflow_async_with_specific_version")
    void test_execute_workflow_async_with_specific_version() {
        // Given
        Map<String, Object> input = Map.of("name", "Async Test");

        // When
        Map<String, Object> response = given()
                .contentType("application/json")
                .body(Map.of("workflowInput", input))
                .queryParam("wait", "false")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(anyOf(is(200), is(202))) // 200 if completed fast, 202 if still running
                .extract()
                .as(Map.class);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).containsKey("instanceId");
        assertThat(response).containsKey("status");
        assertThat(response.get("instanceId")).isNotNull();
    }

    @Test
    @DisplayName("test_execute_workflow_sync_with_latest_version")
    void test_execute_workflow_sync_with_latest_version() {
        // Given
        Map<String, Object> input = Map.of("name", "Latest Version");

        // When
        Map<String, Object> response = given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).containsKey("workflowOutput");

        Map<String, Object> output = (Map<String, Object>) response.get("workflowOutput");
        assertThat(output).containsEntry("greeting", "Latest Version says hello from version v2.0.0!");
    }

    @Test
    @DisplayName("test_execute_workflow_not_found")
    void test_execute_workflow_not_found() {
        // Given
        Map<String, Object> input = Map.of("name", "Test");

        // When/Then
        given()
                .contentType("application/json")
                .body(Map.of("workflowInput", input))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/non-existent-namespace/non-existent-workflow/1.0.0")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("test_execute_workflow_empty_input")
    void test_execute_workflow_empty_input() {
        // Given - empty input (workflow engine should handle this)
        Map<String, Object> emptyInput = Map.of();

        // When
        Map<String, Object> response = given()
                .contentType("application/json")
                .body(Map.of("workflowInput", emptyInput))
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).containsKey("instanceId");
    }

    @Test
    @DisplayName("test_execute_workflow_null_input")
    void test_execute_workflow_null_input() {
        // Given - null input (workflow engine should handle this)

        // When
        Map<String, Object> response = given()
                .contentType("application/json")
                .body("")
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).containsKey("instanceId");
    }

    @Test
    @DisplayName("test_execute_workflow_latest_version_resolution_with_multiple_versions")
    void test_execute_workflow_latest_version_resolution_with_multiple_versions() {
        // Given - we have versions 1.0.0, 1.5.0, and 2.0.0 registered
        Map<String, Object> input = Map.of("name", "Version Test");

        // When - calling without version should resolve to 2.0.0 (latest)
        Map<String, Object> response = given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        // Then - should execute v2.0.0
        assertThat(response).isNotNull();
        assertThat(response).containsKey("workflowOutput");

        Map<String, Object> output = (Map<String, Object>) response.get("workflowOutput");
        assertThat(output).containsEntry("greeting", "Version Test says hello from version v2.0.0!");
    }

    @Test
    @DisplayName("test_execute_specific_older_version_when_multiple_exist")
    void test_execute_specific_older_version_when_multiple_exist() {
        // Given - we have versions 1.0.0, 1.5.0, and 2.0.0 registered
        Map<String, Object> input = Map.of("name", "Old Version");

        // When - explicitly requesting v1.0.0
        Map<String, Object> response = given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.0.0")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        // Then - should execute v1.0.0
        assertThat(response).isNotNull();
        assertThat(response).containsKey("workflowOutput");

        Map<String, Object> output = (Map<String, Object>) response.get("workflowOutput");
        assertThat(output).containsEntry("greeting", "Old Version says hello!");
    }

    @Test
    @DisplayName("test_execute_middle_version_when_multiple_exist")
    void test_execute_middle_version_when_multiple_exist() {
        // Given - we have versions 1.0.0, 1.5.0, and 2.0.0 registered
        Map<String, Object> input = Map.of("name", "Middle Version");

        // When - explicitly requesting v1.5.0
        Map<String, Object> response = given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/test-namespace/simple-greeting/1.5.0")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        // Then - should execute v1.5.0
        assertThat(response).isNotNull();
        assertThat(response).containsKey("workflowOutput");

        Map<String, Object> output = (Map<String, Object>) response.get("workflowOutput");
        assertThat(output).containsEntry("greeting", "Middle Version says hello from version v1.5.0!");
    }
}
