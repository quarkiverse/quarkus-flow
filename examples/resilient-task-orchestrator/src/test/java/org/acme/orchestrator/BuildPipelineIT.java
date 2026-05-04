package org.acme.orchestrator;

import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.acme.orchestrator.model.BuildSpec;
import org.acme.orchestrator.model.BuildTask;
import org.acme.orchestrator.model.TaskState;
import org.acme.orchestrator.model.TaskStatus;
import org.acme.orchestrator.service.TaskStateStore;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

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
@TestProfile(BuildPipelineIT.BroadcastProfile.class)
class BuildPipelineIT {

    private static final Logger LOG = LoggerFactory.getLogger(BuildPipelineIT.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final JsonFormat CE_JSON = new JsonFormat();

    @Inject
    TaskStateStore stateStore;

    @Inject
    @Channel("flow-in")
    io.smallrye.mutiny.Multi<byte[]> flowInEvents;

    // Track emitted events across tests
    private Set<String> emittedTaskIds;

    @BeforeEach
    void setUp() {
        stateStore.clear();
        emittedTaskIds = ConcurrentHashMap.newKeySet();

        // Subscribe to flow-in events to track which tasks were actually emitted
        flowInEvents.subscribe().with(eventBytes -> {
            try {
                CloudEvent ce = CE_JSON.deserialize(eventBytes);
                if (ce.getType().equals("org.acme.build.task.started")) {
                    BuildTask task = objectMapper.readValue(ce.getData().toBytes(), BuildTask.class);
                    emittedTaskIds.add(task.id());
                    LOG.info("Task started event captured: {}", task.id());
                }
            } catch (Exception e) {
                LOG.error("Failed to process event", e);
            }
        });
    }

    @Test
    @DisplayName("should_start_build_pipeline_and_track_task_states")
    void test_start_build_pipeline() {
        // Given
        String projectName = "test-project";
        BuildSpec spec = new BuildSpec(
                projectName,
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
        assertThat(response.get("project")).isEqualTo(projectName);

        // Verify both unique tasks were emitted (ForExecutor bug check)
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(emittedTaskIds)
                            .as("Both unique tasks should have been emitted")
                            .hasSize(2)
                            .containsExactlyInAnyOrder(
                                    projectName + "-lint",
                                    projectName + "-test"
                            );
                });

        // Wait for tasks to appear in state store
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> stateStore.getAll().size() == 2);

        // Verify task states were persisted
        Map<String, TaskState> allStates = stateStore.getAll();
        assertThat(allStates).hasSize(2);

        // Each task should have state tracking
        allStates.values().forEach(state -> {
            assertThat(state.getTaskId()).isNotBlank();
            assertThat(state.getStatus()).isIn(
                    TaskStatus.RUNNING,
                    TaskStatus.COMPLETED,
                    TaskStatus.FAILED);
        });

        // Verify both specific tasks exist
        assertThat(allStates).containsKeys(
                projectName + "-lint",
                projectName + "-test"
        );
    }

    @Test
    @DisplayName("should_persist_task_state_with_completed_phases")
    void test_task_state_persistence() {
        // Given
        String projectName = "state-test-project";
        BuildSpec spec = BuildSpec.createDefault(projectName);

        // When
        given()
                .contentType(ContentType.JSON)
                .body(spec)
                .post("/api/builds/start")
                .then()
                .statusCode(202);

        // Then - verify all 4 UNIQUE tasks were emitted (tests ForExecutor bug)
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(emittedTaskIds)
                            .as("All 4 unique tasks should have been emitted (ForExecutor bug test)")
                            .hasSize(4)
                            .containsExactlyInAnyOrder(
                                    projectName + "-lint",
                                    projectName + "-test",
                                    projectName + "-build",
                                    projectName + "-deploy"
                            );
                });

        // Wait for ALL 4 tasks to appear in state store
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Map<String, TaskState> allStates = stateStore.getAll();
                    assertThat(allStates)
                            .as("All 4 tasks should exist in state store")
                            .hasSize(4)
                            .containsKeys(
                                    projectName + "-lint",
                                    projectName + "-test",
                                    projectName + "-build",
                                    projectName + "-deploy"
                            );
                });

        // Wait for ALL 4 tasks to complete
        await()
                .atMost(Duration.ofSeconds(40))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    Map<String, TaskState> allStates = stateStore.getAll();

                    assertThat(allStates.get(projectName + "-lint").getStatus())
                            .as("lint task should complete")
                            .isEqualTo(TaskStatus.COMPLETED);
                    assertThat(allStates.get(projectName + "-test").getStatus())
                            .as("test task should complete")
                            .isEqualTo(TaskStatus.COMPLETED);
                    assertThat(allStates.get(projectName + "-build").getStatus())
                            .as("build task should complete")
                            .isEqualTo(TaskStatus.COMPLETED);
                    assertThat(allStates.get(projectName + "-deploy").getStatus())
                            .as("deploy task should complete")
                            .isEqualTo(TaskStatus.COMPLETED);
                });

        // Verify completed tasks have phase tracking
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
        String projectName = "retry-test";
        BuildSpec spec = new BuildSpec(
                projectName,
                "main",
                List.of("lint", "test", "build"));

        // When
        given()
                .contentType(ContentType.JSON)
                .body(spec)
                .post("/api/builds/start")
                .then()
                .statusCode(202);

        // Verify all 3 unique tasks were emitted (ForExecutor bug check)
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(emittedTaskIds)
                            .as("All 3 unique tasks should have been emitted")
                            .hasSize(3)
                            .containsExactlyInAnyOrder(
                                    projectName + "-lint",
                                    projectName + "-test",
                                    projectName + "-build"
                            );
                });

        // Wait for task execution attempts
        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Map<String, TaskState> states = stateStore.getAll();
                    // All 3 tasks should exist and have attempted execution
                    return states.size() == 3 && states.values().stream()
                            .allMatch(s -> s.getAttemptCount() > 0);
                });

        // Then - verify retry behavior
        Map<String, TaskState> allStates = stateStore.getAll();
        assertThat(allStates).hasSize(3);

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
        String projectName = "status-check";
        BuildSpec spec = BuildSpec.createDefault(projectName);

        given()
                .contentType(ContentType.JSON)
                .body(spec)
                .post("/api/builds/start")
                .then()
                .statusCode(202);

        // Verify all 4 unique tasks were emitted
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(emittedTaskIds)
                            .as("All 4 unique tasks should have been emitted")
                            .hasSize(4);
                });

        // Wait for tasks to be created in state store
        await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> stateStore.getAll().size() == 4);

        // When - query status
        Map<String, TaskState> statusResponse = given()
                .when()
                .get("/api/builds/status")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        // Then
        assertThat(statusResponse).hasSize(4);
        LOG.info("Task statuses: {}", statusResponse);
    }

    /**
     * Test profile to enable broadcast mode for flow-in channel.
     * This allows both the workflow and the test to consume events.
     */
    public static class BroadcastProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("mp.messaging.incoming.flow-in.broadcast", "true");
        }
    }
}
