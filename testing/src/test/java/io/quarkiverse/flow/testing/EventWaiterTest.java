package io.quarkiverse.flow.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.assertions.AsyncFlowAssertions;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;

public class EventWaiterTest {

    @Test
    void should_wait_for_workflow_started() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            CompletableFuture.runAsync(workflowInstance::start);

            AsyncFlowAssertions.assertWith(store)
                    .workflowStarted()
                    .andAssert()
                    .workflowStarted();
        }
    }

    @Test
    void should_wait_for_task_started() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("myTask", (number) -> {
                            try {
                                Thread.sleep(100); // simulate some work
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return number + 1;
                        }, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            CompletableFuture.runAsync(workflowInstance::start);

            AsyncFlowAssertions.assertWith(store).taskStarted("myTask");
        }
    }

    @Test
    void should_wait_for_task_completed() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            CompletableFuture.runAsync(workflowInstance::start);

            AsyncFlowAssertions.assertWith(store)
                    .taskCompleted("task1");
        }
    }

    @Test
    void should_wait_for_workflow_completed() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for workflow to complete using new API
            AsyncFlowAssertions.assertWith(store)
                    .workflowCompleted()
                    //                    .andAssert()
                    .workflowCompleted();

            // Verify workflow completed
            assertThat(store.getAll()).isNotEmpty();
        }
    }

    @Test
    void should_wait_for_multiple_events_in_sequence() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for events in sequence using new API
            AsyncFlowAssertions.assertWith(store)
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .taskStarted("task2")
                    .taskCompleted("task2")
                    .workflowCompleted();

            // Verify all events were recorded
            assertThat(store.getAll()).hasSizeGreaterThanOrEqualTo(6);
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

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow on a dedicated thread so interrupt propagates to Thread.sleep inside the task
            Thread slowThread = new Thread(() -> workflowInstance.start().join());
            slowThread.setDaemon(true);
            slowThread.start();

            // This should timeout because the task takes 5 seconds but we only wait 100ms
            assertThatThrownBy(() -> AsyncFlowAssertions.assertWith(store)
                    .timeout(Duration.ofMillis(100))
                    .taskCompleted("slowTask"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Timeout");

            // Interrupt the slow thread so it does not bleed into subsequent tests
            slowThread.interrupt();
        }
    }
}
