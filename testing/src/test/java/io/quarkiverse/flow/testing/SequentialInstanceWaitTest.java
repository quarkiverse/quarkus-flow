package io.quarkiverse.flow.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;

/**
 * Tests for sequentially waiting for events from different workflow instances.
 * Demonstrates using waitFor() for one instance, then another instance.
 */
public class SequentialInstanceWaitTest {

    @Test
    void should_wait_for_first_instance_then_second_instance() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("inc", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.shared(); // Shared storage

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            // Start two workflow instances asynchronously
            WorkflowInstance instance1 = def.instance(10L);
            WorkflowInstance instance2 = def.instance(20L);

            CompletableFuture.runAsync(() -> instance1.start().join());
            CompletableFuture.runAsync(() -> instance2.start().join());

            // Wait for instance1 to complete
            store.waitFor()
                    .forInstance(instance1.id())
                    .workflowCompleted()
                    .thenAssert()
                    .forInstance(instance1.id())
                    .workflowStarted()
                    .taskCompleted("inc")
                    .workflowCompleted()
                    .assertAll();

            // Then wait for instance2 to complete
            store.waitFor()
                    .forInstance(instance2.id())
                    .workflowCompleted()
                    .thenAssert()
                    .forInstance(instance2.id())
                    .workflowStarted()
                    .taskCompleted("inc")
                    .workflowCompleted()
                    .assertAll();

            // Verify both instances completed
            assertThat(store.getByInstanceId(instance1.id())).isNotEmpty();
            assertThat(store.getByInstanceId(instance2.id())).isNotEmpty();
        }
    }

    @Test
    void should_wait_for_multiple_instances_with_different_tasks() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (n) -> n + 1, Long.class),
                        FuncDSL.function("task2", (n) -> n * 2, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.shared();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            WorkflowInstance instance1 = def.instance(5L);
            WorkflowInstance instance2 = def.instance(10L);
            WorkflowInstance instance3 = def.instance(15L);

            // Start all instances asynchronously
            CompletableFuture.runAsync(() -> instance1.start().join());
            CompletableFuture.runAsync(() -> instance2.start().join());
            CompletableFuture.runAsync(() -> instance3.start().join());

            // Wait for instance1 task1 completion
            store.waitFor()
                    .forInstance(instance1.id())
                    .taskCompleted("task1")
                    .thenAssert()
                    .forInstance(instance1.id())
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .assertAll();

            // Wait for instance2 task2 completion
            store.waitFor()
                    .forInstance(instance2.id())
                    .taskCompleted("task2")
                    .thenAssert()
                    .forInstance(instance2.id())
                    .taskCompleted("task1")
                    .taskCompleted("task2")
                    .assertAll();

            // Wait for instance3 workflow completion
            store.waitFor()
                    .forInstance(instance3.id())
                    .workflowCompleted()
                    .thenAssert()
                    .forInstance(instance3.id())
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
    void should_wait_for_specific_events_across_multiple_instances() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("process", (n) -> n + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.shared();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            WorkflowInstance instance1 = def.instance(100L);
            WorkflowInstance instance2 = def.instance(200L);

            CompletableFuture.runAsync(() -> instance1.start().join());
            CompletableFuture.runAsync(() -> instance2.start().join());

            // Wait for instance1 to start
            store.waitFor()
                    .forInstance(instance1.id())
                    .workflowStarted();

            // Wait for instance2 to start
            store.waitFor()
                    .forInstance(instance2.id())
                    .workflowStarted();

            // Wait for instance1 task to complete
            store.waitFor()
                    .forInstance(instance1.id())
                    .taskCompleted("process");

            // Wait for instance2 task to complete
            store.waitFor()
                    .forInstance(instance2.id())
                    .taskCompleted("process");

            // Wait for instance1 to complete
            store.waitFor()
                    .forInstance(instance1.id())
                    .workflowCompleted();

            // Wait for instance2 to complete
            store.waitFor()
                    .forInstance(instance2.id())
                    .workflowCompleted();

            // Assert both completed successfully
            FluentEventAssertions.assertThat(store)
                    .forInstance(instance1.id())
                    .workflowCompleted()
                    .assertAll();

            FluentEventAssertions.assertThat(store)
                    .forInstance(instance2.id())
                    .workflowCompleted()
                    .assertAll();
        }
    }
}
