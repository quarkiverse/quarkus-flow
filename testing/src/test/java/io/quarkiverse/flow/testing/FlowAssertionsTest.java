package io.quarkiverse.flow.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.exception.UncheckedInterruptedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.assertions.AsyncFlowAssertions;
import io.quarkiverse.flow.testing.assertions.ConfigurableAssertions;
import io.quarkiverse.flow.testing.assertions.FlowAssertions;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowInstance;

public class FlowAssertionsTest {

    private static final String VALIDATE_MARVEL_VILLAINS = "validateVillain";
    private static final String DR_OCTOPUS = "Dr. Octopus";
    private static final String BRUTUS = "Brutus";
    private static final String HULK_FRIEND = "Hulk";
    private static final String FLASH = "Flash";

    private final Workflow getVillainWorkflow = FuncWorkflowBuilder.workflow("spider", "man", "2.0.0")
            .tasks(FuncDSL.function(VALIDATE_MARVEL_VILLAINS, input -> {

                if (BRUTUS.equals(input)) {
                    throw new IllegalStateException("Brutus is from Popeye not from Marvel");
                }

                if (HULK_FRIEND.equals(input)) {
                    return Boolean.FALSE;
                }

                if (FLASH.equals(input)) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new UncheckedInterruptedException(e);
                    }
                }

                return Boolean.TRUE;
            }, String.class)).build();

    @Test
    @DisplayName("Workflow should start and complete successfully")
    void test_workflow_started_and_completed() {

        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(DR_OCTOPUS);
            instance.start().join();
            FlowAssertions.assertWith(store)
                    .workflowStarted()
                    .workflowCompleted();
        }
    }

    @Test
    @DisplayName("Asserting workflowFailed() on a successfully completed workflow should throw AssertionError")
    void test_workflow_failed_assertion_throws_when_workflow_completed_successfully() {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(DR_OCTOPUS);
            instance.start().join();

            Assertions.assertThrows(AssertionError.class, () -> {
                FlowAssertions.assertWith(store)
                        .workflowStarted()
                        .workflowFailed(); // fails because the workflow completed successfully
            });
        }
    }

    @Test
    @DisplayName("Workflow and the validateVillain task should start and complete successfully")
    void test_workflow_and_task_started_and_completed() {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(DR_OCTOPUS);
            instance.start().join();
            FlowAssertions.assertWith(store)
                    .workflowStarted()
                    .workflowCompleted()
                    .taskStarted(VALIDATE_MARVEL_VILLAINS)
                    .taskCompleted(VALIDATE_MARVEL_VILLAINS);
        }
    }

    @Test
    @DisplayName("Workflow and the validateVillain task should fail")
    void test_workflow_and_the_validate_villain_task_should_fail() throws InterruptedException {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance("Brutus");

            CompletableFuture.runAsync(instance::start);

            Thread.sleep(200); // wait for the workflow to process the task and fail

            FlowAssertions.assertWith(store)
                    .workflowStarted()
                    .taskFailed(VALIDATE_MARVEL_VILLAINS)
                    .workflowFailed();
        }
    }

    @Test
    @DisplayName("Asserting taskFailed() on a successfully completed workflow should throw AssertionError")
    void test_task_failed_assertion_throws_when_workflow_completed_successfully() {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(DR_OCTOPUS);
            instance.start().join();

            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .taskFailed(VALIDATE_MARVEL_VILLAINS));
        }
    }

    @Test
    @DisplayName("Should have 1 WORKFLOW_STARTED event")
    void should_have_one_workflow_started_event() {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(DR_OCTOPUS);
            instance.start().join();

            FlowAssertions.assertWith(store)
                    .hasWorkflowStartedEventCount(1);

            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .hasWorkflowStartedEventCount(10));
        }
    }

    @Test
    @DisplayName("Should have 1 WORKFLOW_COMPLETED event")
    void should_have_one_workflow_completed_event() {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(DR_OCTOPUS);
            instance.start().join();

            FlowAssertions.assertWith(store)
                    .hasWorkflowCompletedEventCount(1);

            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .hasWorkflowCompletedEventCount(10));
        }
    }

    @Test
    @DisplayName("Should have 1 WORKFLOW_FAILED event")
    void should_have_one_workflow_failed_event() throws InterruptedException {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(BRUTUS);

            CompletableFuture.runAsync(instance::start);

            Thread.sleep(200); // wait for the workflow to process the task and fail

            FlowAssertions.assertWith(store)
                    .hasWorkflowFailedEventCount(1);

            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .hasWorkflowFailedEventCount(10));
        }
    }

    @Test
    @DisplayName("Should have 1 WORKFLOW_CANCELLED event")
    void should_have_one_workflow_cancelled_event() throws InterruptedException {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(FLASH);

            CompletableFuture.runAsync(instance::start);

            Thread.sleep(200); // wait for the workflow to enter the Flash task's 1-second sleep

            instance.cancel();

            Thread.sleep(200); // wait for the cancellation event to be recorded

            FlowAssertions.assertWith(store)
                    .hasWorkflowCanceledEventCount(1);

            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .hasWorkflowCanceledEventCount(10));
        }
    }

    @Test
    @DisplayName("Should have 1 WORKFLOW_SUSPENDED and WORKFLOW_RESUMED event")
    void should_have_one_workflow_suspended_and_resumed_event() throws InterruptedException {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(FLASH);

            CompletableFuture.runAsync(instance::start);

            Thread.sleep(200);

            instance.suspend();

            instance.resume();

            Thread.sleep(200);

            FlowAssertions.assertWith(store)
                    .hasWorkflowSuspendedEventCount(1)
                    .hasWorkflowResumedEventCount(1);

            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .hasWorkflowSuspendedEventCount(10));
            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .hasWorkflowResumedEventCount(10));
        }
    }

    @Test
    @DisplayName("workflowCancelled() asserts a WORKFLOW_CANCELLED event exists")
    void should_assert_workflow_cancelled_event() throws InterruptedException {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(FLASH);

            CompletableFuture.runAsync(instance::start);
            Thread.sleep(200);
            instance.cancel();
            Thread.sleep(200);

            FlowAssertions.assertWith(store)
                    .workflowCancelled();

            Assertions.assertThrows(AssertionError.class,
                    () -> FlowAssertions.assertWith(WorkflowEventStore.createInstance())
                            .workflowCancelled());
        }
    }

    @Test
    @DisplayName("workflowSuspended() and workflowResumed() assert the corresponding events exist")
    void should_assert_workflow_suspended_and_resumed_events() throws InterruptedException {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(FLASH);

            CompletableFuture.runAsync(instance::start);
            Thread.sleep(200);
            instance.suspend();
            Thread.sleep(100);
            instance.resume();
            Thread.sleep(300);

            FlowAssertions.assertWith(store)
                    .workflowSuspended()
                    .workflowResumed();
        }
    }

    @Test
    @DisplayName("taskCancelled() asserts a TASK_CANCELLED event exists for the given task")
    void should_assert_task_cancelled_event() throws InterruptedException {
        // A listen task registers itself as a cancelable CompletableFuture,
        // so cancelling the workflow properly fires TASK_CANCELLED (not TASK_FAILED).
        Workflow listenWorkflow = FuncWorkflowBuilder.workflow("listen", "cancel", "1.0.0")
                .tasks(
                        FuncDSL.listen("waitingForEvent", FuncDSL.toOne("never-comes-event")))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(listenWorkflow);
            WorkflowInstance instance = def.instance("input");

            CompletableFuture.runAsync(instance::start);
            Thread.sleep(200);
            instance.cancel();
            Thread.sleep(200);

            FlowAssertions.assertWith(store)
                    .taskCancelled("waitingForEvent");

            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .taskCancelled("nonExistentTask"));
        }
    }

    @Test
    @DisplayName("taskSuspended() and taskResumed() throw AssertionError because the SDK does not fire task-level suspend/resume events")
    void task_suspended_and_resumed_assertions_throw_when_no_task_events_fired() throws InterruptedException {
        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            WorkflowDefinition def = app.workflowDefinition(getVillainWorkflow);
            WorkflowInstance instance = def.instance(FLASH);

            CompletableFuture.runAsync(instance::start);
            Thread.sleep(200);
            instance.suspend();
            Thread.sleep(100);
            instance.resume();
            Thread.sleep(300);

            // Workflow-level suspend/resume events ARE fired
            FlowAssertions.assertWith(store)
                    .workflowSuspended()
                    .workflowResumed();

            // Task-level suspend/resume events are NOT fired by the current SDK implementation
            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .taskSuspended(VALIDATE_MARVEL_VILLAINS));
            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .taskResumed(VALIDATE_MARVEL_VILLAINS));
        }
    }

    @Test
    @DisplayName("taskRetried() asserts a TASK_RETRIED event exists for the given task")
    void should_assert_task_retried_event() {
        AtomicInteger attempts = new AtomicInteger(0);

        Workflow retryWorkflow = FuncWorkflowBuilder.workflow("retry", "test", "1.0.0")
                .tasks(
                        FuncDSL.tryCatch("retryBlock", tryTask -> tryTask
                                .tryHandler(FuncDSL.function("retriableTask", input -> {
                                    if (attempts.getAndIncrement() < 2) {
                                        throw new WorkflowException(
                                                WorkflowError.error("https://example.com/error/transient", 503).build());
                                    }
                                    return "success";
                                }, String.class))
                                .catchHandler(catchBlock -> catchBlock
                                        .retry(retry -> retry
                                                .backoff(b -> {
                                                })
                                                .delay(d -> d.milliseconds(10))
                                                .limit(limit -> limit
                                                        .attempt(attempt -> attempt.count(3)))))))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();
        try (WorkflowApplication app = buildWorkflowAppWith(store)) {
            app.workflowDefinition(retryWorkflow)
                    .instance("go")
                    .start()
                    .join();

            FlowAssertions.assertWith(store)
                    .taskRetried("retriableTask");

            Assertions.assertThrows(AssertionError.class, () -> FlowAssertions.assertWith(store)
                    .taskRetried("nonExistentTask"));
        }
    }

    @Test
    @DisplayName("Should use ordered assertions with strictly() mode")
    void should_use_ordered_assertions_with_inOrder() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(5L);
            workflowInstance.start().join();

            // Strict ordering - events must occur in exact sequence
            FlowAssertions.assertWith(workflowEventStore)
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
    @DisplayName("Should use unordered assertions by default")
    void should_use_unordered_assertions_by_default() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("taskA", (number) -> number + 1, Long.class),
                        FuncDSL.function("taskB", (number) -> number * 2, Long.class),
                        FuncDSL.function("taskC", (number) -> number - 3, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // No inOrder() - just verify events exist, order doesn't matter
            FlowAssertions.assertWith(store)
                    .workflowStarted()
                    .taskStarted("taskC") // Can check in any order
                    .taskStarted("taskA")
                    .taskCompleted("taskB")
                    .taskStarted("taskB")
                    .taskCompleted("taskA")
                    .taskCompleted("taskC")
                    .workflowCompleted();
        }
    }

    @Test
    @DisplayName("Should count specific event types")
    void should_count_specific_event_types() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class),
                        FuncDSL.function("task3", (number) -> number - 5, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Count specific event types
            FlowAssertions.assertWith(store)
                    .workflowStarted() // Must call assertion first to get FluentEventAssertions
                    .hasWorkflowStartedEventCount(1)
                    .hasWorkflowCompletedEventCount(1)
                    .hasTaskStartedEventCount(3)
                    .hasTaskCompletedEventCount(3);
        }
    }

    @Test
    @DisplayName("Should verify task execution order")
    void should_verify_task_execution_order() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("firstTask", (number) -> number + 1, Long.class),
                        FuncDSL.function("secondTask", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Verify one task completed before another
            FlowAssertions.assertWith(store)
                    .workflowStarted()
                    .assertTask("firstTask").completedBeforeOrEqualTo("secondTask");
        }
    }

    @Test
    @DisplayName("Should verify workflow completion time")
    void should_verify_workflow_completion_time() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("quickTask", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore store = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(store))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            // Verify workflow completed within a time limit
            FlowAssertions.assertWith(store)
                    .workflowStarted() // Must call assertion first
                    .workflowCompletedWithin(Duration.ofSeconds(5));
        }
    }

    @Test
    @DisplayName("Should verify all events for specific instance")
    void should_verify_all_events_for_specific_instance() {
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
            workflowInstance.start().join();

            // Verify all events belong to the same instance
            FlowAssertions.assertWith(store)
                    .workflowStarted() // Must call assertion first
                    .allEventsForInstance(workflowInstance.id());
        }
    }

    @Test
    @DisplayName("Should verify output of completed workflow")
    void should_verify_output_of_completed_workflow() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("doubleIt", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(5L);
            workflowInstance.start().join();

            // Verify workflow output
            FlowAssertions.assertWith(workflowEventStore)
                    .strictly()
                    .workflowStarted()
                    .taskStarted("doubleIt")
                    .taskCompleted("doubleIt")
                    .workflowCompleted()
                    .withOutput(output -> {
                        assertThat(output.asNumber().orElseThrow()).isEqualTo(10L);
                    });
        }
    }

    @Test
    @DisplayName("Should reset and reuse assertions")
    void should_reset_and_reuse_assertions() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);
            workflowInstance.start().join();

            ConfigurableAssertions configurableAssertions = FlowAssertions.assertWith(workflowEventStore);

            // First pass - ordered assertions
            configurableAssertions.strictly()
                    .workflowStarted()
                    .taskStarted("task1");

            // Reset and verify again from the beginning
            configurableAssertions.reset()
                    .strictly()
                    .reset()
                    .workflowStarted()
                    .taskStarted("task1")
                    .taskCompleted("task1")
                    .workflowCompleted();
        }
    }

    @Test
    @DisplayName("Should combine event waiter with fluent assertions")
    void should_combine_event_waiter_with_fluent_assertions() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("task1", (number) -> number + 1, Long.class),
                        FuncDSL.function("task2", (number) -> number * 2, Long.class))
                .build();

        WorkflowEventStore workflowEventStore = WorkflowEventStore.createInstance();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowInstance workflowInstance = def.instance(10L);

            // Start workflow asynchronously
            CompletableFuture.runAsync(() -> workflowInstance.start().join());

            // Wait for specific events and verify the complete sequence in one chain
            AsyncFlowAssertions.assertWith(workflowEventStore)
                    .taskStarted("task1")
                    .taskCompleted("task1")
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

    private static WorkflowApplication buildWorkflowAppWith(WorkflowEventStore eventStore) {
        return WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(eventStore))
                .build();
    }

}
