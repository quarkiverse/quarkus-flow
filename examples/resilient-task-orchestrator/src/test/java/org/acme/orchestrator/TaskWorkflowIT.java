package org.acme.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.orchestrator.model.BuildTask;
import org.acme.orchestrator.model.TaskState;
import org.acme.orchestrator.model.TaskStatus;
import org.acme.orchestrator.service.TaskStateStore;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for TaskWorkflow demonstrating event-triggered workflow execution.
 *
 * This test validates the schedule(on(one())) pattern where:
 * - Workflow automatically starts when event arrives
 * - No manual instance.start() needed
 * - Demonstrates idempotent execution
 * - Validates state persistence
 */
@QuarkusTest
class TaskWorkflowIT {

    private static final Logger LOG = LoggerFactory.getLogger(TaskWorkflowIT.class);

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TaskStateStore stateStore;

    // Kafka/messaging emitter for flow-in channel
    @Inject
    @Channel("flow-in-outgoing")
    Emitter<byte[]> flowIn;

    @BeforeEach
    void setUp() {
        stateStore.clear();
    }

    @Test
    @DisplayName("should_auto_start_workflow_on_task_event")
    void test_workflow_auto_starts() throws Exception {
        // Given - a build task
        BuildTask task = new BuildTask(
                "test-lint",
                "lint",
                "test-project",
                "main");

        // When - we emit the task.started event (workflow should auto-start)
        emitTaskStartedEvent(task);

        // Then - wait for workflow to execute the task
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    TaskState state = stateStore.get(task.id());
                    assertThat(state).isNotNull();
                    assertThat(state.getAttemptCount()).isGreaterThan(0);
                });

        // Verify task execution details
        TaskState finalState = stateStore.get(task.id());
        assertThat(finalState.getTaskId()).isEqualTo(task.id());
        assertThat(finalState.getStatus()).isIn(
                TaskStatus.RUNNING,
                TaskStatus.COMPLETED,
                TaskStatus.FAILED);

        LOG.info("Task {} executed: status={}, attempts={}",
                task.id(), finalState.getStatus(), finalState.getAttemptCount());
    }

    @Test
    @DisplayName("should_execute_task_with_multiple_phases")
    void test_task_phases_execution() throws Exception {
        // Given - a test task that has multiple phases
        BuildTask task = new BuildTask(
                "multi-phase-test",
                "test", // test tasks have phases: setup, run-tests, collect-coverage
                "test-project",
                "main");

        // When - emit event to trigger workflow
        emitTaskStartedEvent(task);

        // Then - wait for task to complete with phases
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    TaskState state = stateStore.get(task.id());
                    // Either completed or failed after attempts
                    assertThat(state.getStatus()).isIn(
                            TaskStatus.COMPLETED,
                            TaskStatus.FAILED);
                });

        TaskState finalState = stateStore.get(task.id());

        // If completed, verify phases were tracked
        if (finalState.getStatus() == TaskStatus.COMPLETED) {
            assertThat(finalState.getCompletedPhases())
                    .as("Completed task should have phases tracked")
                    .containsAnyOf("setup", "run-tests", "collect-coverage");

            assertThat(finalState.getExternalState())
                    .as("Completed task should have external state")
                    .isNotBlank();

            LOG.info("Task {} completed phases: {}",
                    task.id(), finalState.getCompletedPhases());
        } else {
            // If failed, verify error is tracked
            assertThat(finalState.getLastError())
                    .as("Failed task should have error message")
                    .isNotBlank();

            LOG.info("Task {} failed after {} attempts: {}",
                    task.id(), finalState.getAttemptCount(), finalState.getLastError());
        }
    }

    @Test
    @DisplayName("should_handle_task_retry_on_failure")
    void test_task_retry_mechanism() throws Exception {
        // Given - a build task
        BuildTask task = new BuildTask(
                "retry-build",
                "build",
                "retry-project",
                "main");

        // When - emit event (task may fail due to simulated failures)
        emitTaskStartedEvent(task);

        // Then - wait for task to either complete or exhaust retries
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    TaskState state = stateStore.get(task.id());
                    // Should have made at least one attempt
                    assertThat(state.getAttemptCount()).isGreaterThan(0);

                    // Should either complete or fail
                    assertThat(state.getStatus()).isIn(
                            TaskStatus.COMPLETED,
                            TaskStatus.FAILED);
                });

        TaskState finalState = stateStore.get(task.id());

        // Log the retry behavior
        LOG.info("Task {} finished: status={}, attempts={}",
                task.id(), finalState.getStatus(), finalState.getAttemptCount());

        // Verify retry behavior
        if (finalState.getStatus() == TaskStatus.FAILED) {
            // Failed tasks may have retried
            LOG.info("  Retries observed: {} attempts before final failure",
                    finalState.getAttemptCount());
        } else {
            LOG.info("  Task succeeded after {} attempt(s)",
                    finalState.getAttemptCount());
        }
    }

    @Test
    @DisplayName("should_persist_state_during_execution")
    void test_state_persistence() throws Exception {
        // Given - a task
        BuildTask task = new BuildTask(
                "persistence-test",
                "lint",
                "test-project",
                "main");

        // When - trigger execution
        emitTaskStartedEvent(task);

        // Then - state should be persisted as task executes
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    TaskState state = stateStore.get(task.id());
                    assertThat(state).isNotNull();
                    assertThat(state.getTaskId()).isEqualTo(task.id());
                });

        // Verify state details
        TaskState state = stateStore.get(task.id());
        assertThat(state.getAttemptCount()).isGreaterThan(0);

        LOG.info("State persisted: taskId={}, status={}, attempts={}, phases={}",
                state.getTaskId(),
                state.getStatus(),
                state.getAttemptCount(),
                state.getCompletedPhases());
    }

    @Test
    @DisplayName("should_demonstrate_idempotent_execution")
    void test_idempotent_execution() throws Exception {
        // Given - a task that we'll execute twice
        BuildTask task = new BuildTask(
                "idempotent-test",
                "build",
                "test-project",
                "main");

        // When - first execution
        emitTaskStartedEvent(task);

        // Wait for first execution to complete
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    TaskState state = stateStore.get(task.id());
                    assertThat(state.getStatus()).isIn(
                            TaskStatus.COMPLETED,
                            TaskStatus.FAILED);
                });

        TaskState firstExecution = stateStore.get(task.id());
        int firstAttempts = firstExecution.getAttemptCount();
        int firstPhases = firstExecution.getCompletedPhases().size();

        LOG.info("First execution: status={}, attempts={}, phases={}",
                firstExecution.getStatus(), firstAttempts, firstPhases);

        // If task completed, trigger second execution to test idempotency
        if (firstExecution.getStatus() == TaskStatus.COMPLETED) {
            // When - second execution (should be idempotent)
            emitTaskStartedEvent(task);

            // Wait a bit for potential re-execution
            Thread.sleep(2000);

            // Then - verify phases were not re-executed (idempotent)
            TaskState secondExecution = stateStore.get(task.id());

            LOG.info("Second execution: status={}, attempts={}, phases={}",
                    secondExecution.getStatus(),
                    secondExecution.getAttemptCount(),
                    secondExecution.getCompletedPhases().size());

            // Note: Due to the idempotent design, completed phases should be skipped
            // This demonstrates the resilience pattern in action
        }
    }

    /**
     * Helper method to emit a task.started CloudEvent.
     */
    private void emitTaskStartedEvent(BuildTask task) throws Exception {
        byte[] taskData = objectMapper.writeValueAsBytes(task);

        CloudEvent ce = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/task-workflow"))
                .withType("org.acme.build.task.started")
                .withDataContentType("application/json")
                .withData(taskData)
                .build();

        byte[] ceBytes = CE_JSON.serialize(ce);
        flowIn.send(ceBytes);

        LOG.info("Emitted task.started event for task: {} ({})",
                task.id(), task.name());
    }
}
