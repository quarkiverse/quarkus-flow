package io.quarkiverse.flow.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Comprehensive test suite demonstrating all features of the testing framework.
 */
public class ComprehensiveTestingFrameworkTest {

    @Test
    void should_demonstrate_basic_event_recording() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("increment", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            WorkflowModel result = workflowInstance.start().join();

            // Verify events were recorded
            assertThat(workflowEventStore.getAll()).isNotEmpty();
            assertThat(workflowEventStore.getAll()).hasSizeGreaterThanOrEqualTo(4);
        }
    }

    @Test
    void should_use_ordered_assertions_with_inOrder() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(5L);
            workflowInstance.start().join();

            // Strict ordering - events must occur in exact sequence
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
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
    void should_use_unordered_assertions_by_default() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("taskA", (number) -> number + 1, Long.class),
                        FuncDSL.function("taskB", (number) -> number * 2, Long.class),
                        FuncDSL.function("taskC", (number) -> number - 3, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // No inOrder() - just verify events exist, order doesn't matter
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .workflowStarted()
                    .taskStarted("taskC") // Can check in any order
                    .taskStarted("taskA")
                    .taskCompleted("taskB")
                    .taskStarted("taskB")
                    .taskCompleted("taskA")
                    .taskCompleted("taskC")
                    .workflowCompleted()
                    .assertAll();
        }
    }

    @Test
    void should_count_specific_event_types() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class),
                        FuncDSL.function("task3", (number) -> number - 5, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Count specific event types
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .workflowStarted() // Must call assertion first to get FluentEventAssertions
                    .hasWorkflowStartedEventCount(1)
                    .hasWorkflowCompletedEventCount(1)
                    .hasTaskStartedEventCount(3)
                    .hasTaskCompletedEventCount(3)
                    .assertAll();
        }
    }

    @Test
    void should_verify_task_execution_order() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("firstTask", (number) -> number + 1, Long.class),
                        FuncDSL.function("secondTask", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Verify one task completed before another
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .workflowStarted() // Must call assertion first
                    .taskCompletedBefore("firstTask", "secondTask")
                    .assertAll();
        }
    }

    @Test
    void should_verify_workflow_completion_time() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("quickTask", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Verify workflow completed within a time limit
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .workflowStarted() // Must call assertion first
                    .workflowCompletedWithin(Duration.ofSeconds(5))
                    .assertAll();
        }
    }

    @Test
    void should_verify_all_events_for_specific_instance() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Verify all events belong to the same instance
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .workflowStarted() // Must call assertion first
                    .allEventsForInstance(workflowInstance.id())
                    .assertAll();
        }
    }

    @Test
    void should_verify_output_of_completed_workflow() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("doubleIt", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(5L);
            workflowInstance.start().join();

            // Verify workflow output
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .inOrder()
                    .workflowStarted()
                    .taskStarted("doubleIt")
                    .taskCompleted("doubleIt")
                    .workflowCompleted()
                    .withOutput(output -> {
                        assertThat(output.asNumber().orElseThrow()).isEqualTo(10L);
                    })
                    .assertAll();
        }
    }

    @Test
    void should_reset_and_reuse_assertions() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            ConfigurableAssertions configurableAssertions = FluentEventAssertions.assertThat(workflowEventStore.getAll());

            // First pass - ordered assertions
            configurableAssertions.inOrder()
                    .workflowStarted()
                    .taskStarted("task1");

            // Reset and verify again from the beginning
            configurableAssertions.reset()
                    .inOrder()
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .workflowCompleted()
                    .assertAll();
        }
    }

    @Test
    void should_combine_event_waiter_with_fluent_assertions() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            java.util.concurrent.CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for specific events and verify the complete sequence in one chain
            workflowEventStore.waitFor()
                    .taskStarted("task1")
                    .taskCompleted("task1")
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

    @Test
    void should_verify_multiple_workflows_in_same_store() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.shared(); // Use shared storage for async

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            // Run first workflow instance
            WorkflowInstance instance1 = def.instance(10L);
            instance1.start().join();

            // Run second workflow instance
            WorkflowInstance instance2 = def.instance(20L);
            instance2.start().join();

            // Verify we have events from both instances
            assertThat(workflowEventStore.getAll())
                    .hasSizeGreaterThanOrEqualTo(8); // At least 4 events per instance

            // Verify each instance separately
            FluentEventAssertions.assertThat(workflowEventStore.getEventsForInstance(instance1.id()))
                    .workflowStarted() // Must call assertion first
                    .hasWorkflowStartedEventCount(1)
                    .hasWorkflowCompletedEventCount(1)
                    .assertAll();

            FluentEventAssertions.assertThat(workflowEventStore.getEventsForInstance(instance2.id()))
                    .workflowStarted() // Must call assertion first
                    .hasWorkflowStartedEventCount(1)
                    .hasWorkflowCompletedEventCount(1)
                    .assertAll();
        }
    }
}
