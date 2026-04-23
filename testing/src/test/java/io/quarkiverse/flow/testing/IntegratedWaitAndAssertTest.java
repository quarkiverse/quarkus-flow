package io.quarkiverse.flow.testing;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test demonstrating integrated wait and assert functionality in FluentEventAssertions.
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
                        FuncDSL.function("task2", (number) -> number * 2, Long.class)
                )
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for events and then assert - all in one fluent chain
            FluentEventAssertions.assertThat(workflowEventStore)
                    .waitForWorkflowStarted()
                    .waitForTaskStarted("task1")
                    .waitForTaskCompleted("task1")
                    .waitForTaskStarted("task2")
                    .waitForTaskCompleted("task2")
                    .waitForWorkflowCompleted()
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

    @Test
    void should_wait_with_custom_timeout() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("quickTask", (number) -> number + 1, Long.class)
                )
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Configure timeout and wait
            FluentEventAssertions.assertThat(workflowEventStore)
                    .waitTimeout(Duration.ofSeconds(10))
                    .waitForWorkflowCompleted()
                    .workflowCompleted()
                    .assertAll();
        }
    }

    @Test
    void should_wait_for_specific_task_completion() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class),
                        FuncDSL.function("task3", (number) -> number - 5, Long.class)
                )
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for specific task and verify
            FluentEventAssertions.assertThat(workflowEventStore)
                    .waitForTaskCompleted("task2")
                    .taskCompleted("task2")
                    .assertAll();
        }
    }

    @Test
    void should_combine_wait_and_unordered_assertions() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("taskA", (number) -> number + 1, Long.class),
                        FuncDSL.function("taskB", (number) -> number * 2, Long.class),
                        FuncDSL.function("taskC", (number) -> number - 3, Long.class)
                )
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for completion, then verify all tasks ran (order doesn't matter)
            FluentEventAssertions.assertThat(workflowEventStore)
                    .waitForWorkflowCompleted()
                    .taskStarted("taskA")
                    .taskStarted("taskB")
                    .taskStarted("taskC")
                    .taskCompleted("taskA")
                    .taskCompleted("taskB")
                    .taskCompleted("taskC")
                    .assertAll();
        }
    }

    @Test
    void should_wait_and_verify_output() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("multiply", (number) -> number * 3, Long.class)
                )
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(5L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for completion and verify output
            FluentEventAssertions.assertThat(workflowEventStore)
                    .waitForWorkflowCompleted(Duration.ofSeconds(5))
                    .inOrder()
                    .workflowStarted()
                    .taskStarted("multiply")
                    .taskCompleted("multiply")
                    .workflowCompleted()
                    .withOutput(output -> {
                        assertThat(output.asNumber()).isEqualTo(15L);
                    })
                    .assertAll();
        }
    }

    @Test
    void should_use_both_creation_methods() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class)
                )
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Method 1: Create with WorkflowEventStore (supports waiting)
            FluentEventAssertions.assertThat(workflowEventStore)
                    .workflowStarted()
                    .taskStarted("task1")
                    .assertAll();

            // Method 2: Create with events list (no waiting support)
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .inOrder()
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .workflowCompleted()
                    .assertAll();
        }
    }
}
