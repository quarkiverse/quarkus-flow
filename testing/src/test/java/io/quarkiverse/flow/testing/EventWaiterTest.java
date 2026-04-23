package io.quarkiverse.flow.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;

/**
 * Comprehensive tests for AsyncFluentEventAssertions (EventWaiter) functionality.
 */
public class EventWaiterTest {

    @Test
    void should_wait_for_workflow_started() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore(true); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for workflow to start using new API
            workflowEventStore.waitFor()
                    .workflowStarted()
                    .thenAssert()
                    .workflowStarted()
                    .assertAll();

            // Verify the event was recorded
            assertThat(workflowEventStore.getAll())
                    .isNotEmpty()
                    .anyMatch(e -> e.getType() == io.quarkiverse.flow.testing.events.EventType.WORKFLOW_STARTED);
        }
    }

    @Test
    void should_wait_for_task_started() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("myTask", (number) -> {
                            try {
                                Thread.sleep(100); // Simulate some work
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return number + 1;
                        }, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore(true); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for specific task to start using new API
            workflowEventStore.waitFor()
                    .taskStarted("myTask")
                    .thenAssert()
                    .taskStarted("myTask")
                    .assertAll();

            // Verify the task started event was recorded
            assertThat(workflowEventStore.getAll())
                    .anyMatch(e -> e.getType() == io.quarkiverse.flow.testing.events.EventType.TASK_STARTED &&
                            e.getTaskName().map("myTask"::equals).orElse(false));
        }
    }

    @Test
    void should_wait_for_task_completed() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore(true); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for task to complete using new API
            workflowEventStore.waitFor()
                    .taskCompleted("task1")
                    .thenAssert()
                    .taskCompleted("task1")
                    .assertAll();

            // Verify the task completed
            assertThat(workflowEventStore.getAll())
                    .anyMatch(e -> e.getType() == io.quarkiverse.flow.testing.events.EventType.TASK_COMPLETED &&
                            e.getTaskName().map("task1"::equals).orElse(false));
        }
    }

    @Test
    void should_wait_for_workflow_completed() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore(true); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for workflow to complete using new API
            workflowEventStore.waitFor()
                    .workflowCompleted()
                    .thenAssert()
                    .workflowCompleted()
                    .assertAll();

            // Verify workflow completed
            assertThat(workflowEventStore.getAll())
                    .anyMatch(e -> e.getType() == io.quarkiverse.flow.testing.events.EventType.WORKFLOW_COMPLETED);
        }
    }

    @Test
    void should_wait_for_multiple_events_in_sequence() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore(true); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for events in sequence using new API
            workflowEventStore.waitFor()
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .taskStarted("task2")
                    .taskCompleted("task2")
                    .workflowCompleted()
                    .thenAssert()
                    .assertAll();

            // Verify all events were recorded
            assertThat(workflowEventStore.getAll()).hasSizeGreaterThanOrEqualTo(6);
        }
    }

    @Test
    void should_handle_custom_timeout() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("slowTask", (number) -> {
                            try {
                                Thread.sleep(5000); // Very slow task
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return number + 1;
                        }, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore(true); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // This should timeout because the task takes 5 seconds but we only wait 100ms
            assertThatThrownBy(() -> workflowEventStore.waitFor()
                    .timeout(Duration.ofMillis(100))
                    .taskCompleted("slowTask")
                    .thenAssert()
                    .assertAll())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Timeout");
        }
    }

    @Test
    void should_work_with_fluent_assertions_after_waiting() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore(true); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for workflow to complete and verify the complete event sequence
            workflowEventStore.waitFor()
                    .workflowCompleted()
                    .thenAssert()
                    .inOrder()
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .taskStarted("task2")
                    .taskCompleted("task2")
                    .workflowCompleted()
                    .assertAll();
        }
    }
}