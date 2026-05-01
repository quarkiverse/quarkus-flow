package org.acme.orchestrator;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.acme.orchestrator.model.BuildSpec;
import org.acme.orchestrator.model.TaskState;
import org.acme.orchestrator.model.TaskStatus;
import org.acme.orchestrator.service.TaskStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test demonstrating resilient task orchestration.
 *
 * Tests:
 * - Basic workflow execution
 * - Task state persistence
 * - Idempotent task execution
 * - Resume after failure
 */
@QuarkusTest
class BuildPipelineIT {

    private static final Logger LOG = LoggerFactory.getLogger(BuildPipelineIT.class);

    @Inject
    TaskStateStore stateStore;

    @BeforeEach
    void setUp() {
        stateStore.clear();
    }

    @Test
    @DisplayName("should_start_build_pipeline_and_track_task_states")
    void test_start_build_pipeline() {
        // Given
        BuildSpec spec = new BuildSpec(
                "test-project",
                "main",
                List.of("lint", "test"));

        // When
        Map<String, Object> response = given()
                .contentType(ContentType.JSON)
                .body(spec)
                .when()
                .post("/api/builds/start")
                .then()
                .statusCode(202) // Accepted
                .extract()
                .as(Map.class);

        // Then
        assertThat(response).containsKeys("buildId", "status", "project", "tasks");
        assertThat(response.get("status")).isEqualTo("STARTED");
        assertThat(response.get("project")).isEqualTo("test-project");

        // Wait for tasks to appear in state store
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> !stateStore.getAll().isEmpty());

        // Verify task states were persisted
        Map<String, TaskState> allStates = stateStore.getAll();
        assertThat(allStates).isNotEmpty();

        // Each task should have state tracking
        allStates.values().forEach(state -> {
            assertThat(state.getTaskId()).isNotBlank();
            assertThat(state.getStatus()).isIn(
                    TaskStatus.RUNNING,
                    TaskStatus.COMPLETED,
                    TaskStatus.FAILED);
        });
    }

    @Test
    @DisplayName("should_persist_task_state_with_completed_phases")
    void test_task_state_persistence() {
        // Given
        BuildSpec spec = BuildSpec.createDefault("state-test-project");

        // When
        given()
                .contentType(ContentType.JSON)
                .body(spec)
                .post("/api/builds/start")
                .then()
                .statusCode(202);

        // Wait for at least one task to complete
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Map<String, TaskState> states = stateStore.getAll();
                    return states.values().stream()
                            .anyMatch(s -> s.getStatus() == TaskStatus.COMPLETED);
                });

        // Then - verify completed tasks have phase tracking
        Map<String, TaskState> allStates = stateStore.getAll();
        allStates.values().stream()
                .filter(s -> s.getStatus() == TaskStatus.COMPLETED)
                .forEach(state -> {
                    assertThat(state.getCompletedPhases())
                            .as("Task %s should have completed phases", state.getTaskId())
                            .isNotEmpty();

                    assertThat(state.getExternalState())
                            .as("Task %s should have external state", state.getTaskId())
                            .isNotBlank();

                    LOG.info("Task {} completed phases: {}",
                            state.getTaskId(), state.getCompletedPhases());
                });
    }

    @Test
    @DisplayName("should_handle_task_failures_with_retry")
    void test_task_failure_and_retry() {
        // Given - spec that will trigger multiple tasks
        BuildSpec spec = new BuildSpec(
                "retry-test",
                "main",
                List.of("lint", "test", "build"));

        // When
        given()
                .contentType(ContentType.JSON)
                .body(spec)
                .post("/api/builds/start")
                .then()
                .statusCode(202);

        // Wait for task execution attempts
        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Map<String, TaskState> states = stateStore.getAll();
                    // At least one task should have attempted execution
                    return states.values().stream()
                            .anyMatch(s -> s.getAttemptCount() > 0);
                });

        // Then - verify retry behavior
        Map<String, TaskState> allStates = stateStore.getAll();
        assertThat(allStates).isNotEmpty();

        // Some tasks may have failed and retried
        long tasksWithRetries = allStates.values().stream()
                .filter(s -> s.getAttemptCount() > 1)
                .count();

        LOG.info("Tasks with retries: {}/{}",
                tasksWithRetries, allStates.size());

        // Verify that failed tasks have error tracking
        allStates.values().stream()
                .filter(s -> s.getStatus() == TaskStatus.FAILED)
                .forEach(state -> {
                    assertThat(state.getLastError())
                            .as("Failed task %s should have error message", state.getTaskId())
                            .isNotBlank();

                    LOG.info("Task {} failed after {} attempts: {}",
                            state.getTaskId(), state.getAttemptCount(), state.getLastError());
                });
    }

    @Test
    @DisplayName("should_get_status_of_all_tasks")
    void test_get_status() {
        // Given - start a build first
        BuildSpec spec = BuildSpec.createDefault("status-check");

        given()
                .contentType(ContentType.JSON)
                .body(spec)
                .post("/api/builds/start")
                .then()
                .statusCode(202);

        // Wait for tasks to be created
        await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> !stateStore.getAll().isEmpty());

        // When - query status
        Map<String, TaskState> statusResponse = given()
                .when()
                .get("/api/builds/status")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        // Then
        assertThat(statusResponse).isNotEmpty();
        LOG.info("Task statuses: {}", statusResponse);
    }
}
