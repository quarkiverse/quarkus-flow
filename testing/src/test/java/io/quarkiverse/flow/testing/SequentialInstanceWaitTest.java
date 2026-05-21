package io.quarkiverse.flow.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

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

        WorkflowEventStore store = WorkflowEventStore.createInstance();

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
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(instance1.id())
                    .workflowCompleted()
                    .configure()
                    .filteringBy(instance1.id())
                    .workflowStarted()
                    .taskCompleted("inc")
                    .workflowCompleted();

            // Then wait for instance2 to complete
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(instance2.id())
                    .workflowCompleted()
                    .configure()
                    .filteringBy(instance2.id())
                    .workflowStarted()
                    .taskCompleted("inc")
                    .workflowCompleted();

            // Verify both instances completed
            assertThat(store.filterByInstanceId(instance1.id())).isNotEmpty();
            assertThat(store.filterByInstanceId(instance2.id())).isNotEmpty();
        }
    }

    @Test
    void should_wait_for_multiple_instances_with_different_tasks() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (n) -> n + 1, Long.class),
                        FuncDSL.function("task2", (n) -> n * 2, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            WorkflowInstance instance1 = def.instance(5L);
            WorkflowInstance b = def.instance(10L);
            WorkflowInstance instance3 = def.instance(15L);

            // Start all instances asynchronously
            CompletableFuture.runAsync(() -> instance1.start().join());
            CompletableFuture.runAsync(() -> b.start().join());
            CompletableFuture.runAsync(() -> instance3.start().join());

            // Wait for instance1 task1 completion
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(instance1.id())
                    .taskCompleted("task1")
                    .configure()
                    .filteringBy(instance1.id())
                    .taskStarted("task1")
                    .taskCompleted("task1");

            // Wait for b task2 completion
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(b.id())
                    .taskCompleted("task2")
                    .configure()
                    .filteringBy(b.id())
                    .taskCompleted("task1")
                    .taskCompleted("task2");

            // Wait for instance3 workflow completion
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(instance3.id())
                    .workflowCompleted()
                    .configure()
                    .reset()
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
    void should_wait_for_specific_events_across_multiple_instances() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("process", (n) -> n + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            WorkflowInstance instance1 = def.instance(100L);
            WorkflowInstance instance2 = def.instance(200L);

            CompletableFuture.runAsync(() -> instance1.start().join());
            CompletableFuture.runAsync(() -> instance2.start().join());

            // Wait for instance1 to start
            AsyncFlowAssertions.assertWith(store).filteringBy(instance1.id())
                    .workflowStarted();

            // Wait for instance2 to start
            AsyncFlowAssertions.assertWith(store).filteringBy(instance2.id())
                    .workflowStarted();

            // Wait for instance1 task to complete
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(instance1.id())
                    .taskCompleted("process");

            // Wait for instance2 task to complete
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(instance2.id())
                    .taskCompleted("process");

            // Wait for instance1 to complete
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(instance1.id())
                    .workflowCompleted();

            // Wait for instance2 to complete
            AsyncFlowAssertions.assertWith(store)
                    .filteringBy(instance2.id())
                    .workflowCompleted();

            // Assert both completed successfully
            FlowAssertions.assertWith(store)
                    .filteringBy(instance1.id())
                    .workflowCompleted();

            FlowAssertions.assertWith(store)
                    .filteringBy(instance2.id())
                    .workflowCompleted();
        }
    }
}
