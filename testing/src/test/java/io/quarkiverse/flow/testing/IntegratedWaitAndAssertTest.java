package io.quarkiverse.flow.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.assertions.AsyncFlowAssertions;
import io.quarkiverse.flow.testing.assertions.FlowAssertions;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;

/**
 * Test demonstrating the new AsyncFluentEventAssertions API for waiting and asserting.
 * Shows the separation of concerns: AsyncFluentEventAssertions for waiting,
 * FluentEventAssertions for asserting.
 */
public class IntegratedWaitAndAssertTest {

    @Test
    void should_wait_and_assert_in_single_chain() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return number + 1;
                        }, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            workflowInstance.start().thenAccept(Assertions::assertNotNull);

            // Wait for events using AsyncFluentEventAssertions, then assert
            AsyncFlowAssertions.assertWith(store)
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .taskStarted("task2")
                    .taskCompleted("task2")
                    .workflowCompleted()
                    .andAssert()
                    .strictly()
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .taskStarted("task2")
                    .taskCompleted("task2")
                    .workflowCompleted();
        }
    }

    @Test
    void should_wait_with_custom_timeout() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("quickTask", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Configure timeout and wait
            AsyncFlowAssertions.assertWith(store)
                    .timeout(Duration.ofSeconds(10))
                    .workflowCompleted()
                    //                    .andAssert()
                    .workflowCompleted();
        }
    }

    @Test
    void should_wait_for_specific_task_completion() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class),
                        FuncDSL.function("task3", (number) -> number - 5, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for specific task and verify
            AsyncFlowAssertions.assertWith(store)
                    .taskCompleted("task2")
                    //                    .andAssert()
                    .taskCompleted("task2");
        }
    }

    @Test
    void should_combine_wait_and_unordered_assertions() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("taskA", (number) -> number + 1, Long.class),
                        FuncDSL.function("taskB", (number) -> number * 2, Long.class),
                        FuncDSL.function("taskC", (number) -> number - 3, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for completion, then verify all tasks ran (order doesn't matter)
            AsyncFlowAssertions.assertWith(store)
                    .workflowCompleted()
                    //                    .andAssert()
                    .taskStarted("taskA")
                    .taskStarted("taskB")
                    .taskStarted("taskC")
                    .taskCompleted("taskA")
                    .taskCompleted("taskB")
                    .taskCompleted("taskC");
        }
    }

    @Test
    void should_wait_and_verify_output() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("multiply", (number) -> number * 3, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(5L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for completion and verify output
            AsyncFlowAssertions.assertWith(store)
                    .timeout(Duration.ofSeconds(5))
                    .workflowCompleted()
                    .andAssert()
                    .strictly()
                    .workflowStarted()
                    .taskStarted("multiply")
                    .taskCompleted("multiply")
                    .workflowCompleted()
                    .withOutput(output -> {
                        assertThat(output.asNumber()).hasValue(15L);
                    });
        }
    }

    @Test
    void should_use_both_creation_methods() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Method 1: Create with WorkflowEventStore (standard assertions)
            FlowAssertions.assertWith(store)
                    .workflowStarted()
                    .taskStarted("task1");

            // Method 2: Create with events list (no waiting support)
            FlowAssertions.assertWith(store)
                    .strictly()
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .workflowCompleted();
        }
    }

    @Test
    void should_use_polling_mode_explicitly() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Use polling mode explicitly with custom poll interval
            AsyncFlowAssertions.assertWith(store)
                    .pollInterval(Duration.ofMillis(10))
                    .timeout(Duration.ofSeconds(5))
                    .workflowCompleted()
                    //                    .andAssert()
                    .workflowCompleted();
        }
    }
}
