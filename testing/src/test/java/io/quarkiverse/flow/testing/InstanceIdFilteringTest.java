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
 * Tests for filtering workflow events by instance ID.
 */
public class InstanceIdFilteringTest {

    @Test
    void should_filter_events_by_instance_id_in_fluent_assertions() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("inc", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.shared();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            // Start two workflow instances
            WorkflowInstance instance1 = def.instance(10L);
            WorkflowInstance instance2 = def.instance(20L);

            instance1.start().join();
            instance2.start().join();

            // Verify we have events from both instances
            assertThat(store.size()).isGreaterThan(0);

            // Filter and assert on instance1
            FluentEventAssertions.assertThat(store)
                    .forInstance(instance1.id())
                    .inOrder()
                    .workflowStarted()
                    .taskStarted("inc")
                    .taskCompleted("inc")
                    .workflowCompleted()
                    .assertAll();

            // Filter and assert on instance2
            FluentEventAssertions.assertThat(store)
                    .forInstance(instance2.id())
                    .workflowStarted()
                    .taskStarted("inc")
                    .taskCompleted("inc")
                    .workflowCompleted()
                    .assertAll();

            // Verify each instance has exactly 4 events (started, task started, task completed, completed)
            assertThat(store.getByInstanceId(instance1.id())).hasSize(4);
            assertThat(store.getByInstanceId(instance2.id())).hasSize(4);
        }
    }

    @Test
    void should_filter_events_by_instance_id_in_async_assertions() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("inc", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.shared();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            // Start two workflow instances asynchronously
            WorkflowInstance instance1 = def.instance(10L);
            WorkflowInstance instance2 = def.instance(20L);

            CompletableFuture.runAsync(() -> instance1.start().join());
            CompletableFuture.runAsync(() -> instance2.start().join());

            // Wait for instance1 completion and assert
            store.waitFor()
                    .forInstance(instance1.id())
                    .workflowCompleted()
                    .thenAssert()
                    .forInstance(instance1.id())
                    .workflowStarted()
                    .taskCompleted("inc")
                    .workflowCompleted()
                    .assertAll();

            // Wait for instance2 completion and assert
            store.waitFor()
                    .forInstance(instance2.id())
                    .workflowCompleted()
                    .thenAssert()
                    .forInstance(instance2.id())
                    .workflowStarted()
                    .taskCompleted("inc")
                    .workflowCompleted()
                    .assertAll();
        }
    }

    @Test
    void should_filter_events_with_ordered_assertions() {
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

            instance1.start().join();
            instance2.start().join();

            // Verify ordered execution for instance1
            FluentEventAssertions.assertThat(store)
                    .forInstance(instance1.id())
                    .inOrder()
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .taskStarted("task2")
                    .taskCompleted("task2")
                    .workflowCompleted()
                    .assertAll();

            // Verify ordered execution for instance2
            FluentEventAssertions.assertThat(store)
                    .forInstance(instance2.id())
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
