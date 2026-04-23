package io.quarkiverse.flow.testing;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Test demonstrating both ordered and unordered event assertions using the inOrder() method.
 */
public class OrderedAndUnorderedAssertionsTest {

    @Test
    void should_support_ordered_assertions_with_inOrder() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            WorkflowModel workflowModel = workflowInstance.start().join();

            // Ordered assertions - events must occur in this exact sequence
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .inOrder() // Enable strict ordering
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .taskStarted("task2")
                    .taskCompleted("task2")
                    .assertAll();
        }
    }

    @Test
    void should_support_unordered_assertions_by_default() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            WorkflowModel workflowModel = workflowInstance.start().join();

            // Unordered assertions - just check that events occurred, regardless of order
            // No inOrder() call means order doesn't matter
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskStarted("task2")
                    .taskCompleted("task1")
                    .taskCompleted("task2")
                    .workflowCompleted()
                    .assertAll();
        }
    }

    @Test
    void should_check_if_task_started_without_caring_about_order() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class),
                        FuncDSL.function("task3", (number) -> number - 5, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            WorkflowModel workflowModel = workflowInstance.start().join();

            // Just verify that specific tasks started, without caring about order
            FluentEventAssertions.assertThat(workflowEventStore.getAll())
                    .taskStarted("task2") // Check task2 started (anywhere in the event list)
                    .taskStarted("task1") // Check task1 started (anywhere in the event list)
                    .taskCompleted("task3") // Check task3 completed (anywhere in the event list)
                    .assertAll();
        }
    }
}
