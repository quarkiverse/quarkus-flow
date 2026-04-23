package io.quarkiverse.flow.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Fluent API for asserting on workflow event sequences.
 * Provides a chainable interface for verifying workflow execution order, task completion,
 * and event properties in tests.
 * <p>
 * Example usage:
 *
 * <pre>
 * eventRecorder.assertThat()
 *         .workflowStarted()
 *         .taskStarted("task1")
 *         .taskCompleted("task1")
 *         .workflowCompleted();
 * </pre>
 */
public class FluentEventAssertions {

    private List<RecordedWorkflowEvent> events;
    private final WorkflowEventStore eventStore;
    private int currentIndex = 0;
    private boolean strictOrder = false;
    private EventWaiter eventWaiter;

    SoftAssertions softAssertions = new SoftAssertions();

    public static FluentEventAssertions assertThat(List<RecordedWorkflowEvent> events) {
        return new FluentEventAssertions(events, null);
    }

    public static FluentEventAssertions assertThat(WorkflowEventStore eventStore) {
        return new FluentEventAssertions(eventStore.getAll(), eventStore);
    }

    public FluentEventAssertions(List<RecordedWorkflowEvent> events) {
        this(events, null);
    }

    public FluentEventAssertions(List<RecordedWorkflowEvent> events, WorkflowEventStore eventStore) {
        this.events = events;
        this.eventStore = eventStore;
        if (eventStore != null) {
            this.eventWaiter = new EventWaiter(eventStore);
        }
    }

    /**
     * Refreshes the events list from the event store.
     * This is automatically called after wait operations.
     */
    private void refreshEvents() {
        if (eventStore != null) {
            this.events = eventStore.getAll();
        }
    }

    /**
     * Enables strict ordering mode. When enabled, all subsequent assertions will verify
     * that events occur in the exact order specified. When disabled (default), assertions
     * only verify that events exist somewhere in the event list.
     *
     * @return this for method chaining
     */
    public FluentEventAssertions inOrder() {
        this.strictOrder = true;
        return this;
    }

    // EventWaiter Integration

    /**
     * Waits for a workflow started event before continuing with assertions.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitForWorkflowStarted() {
        ensureEventWaiterAvailable();
        eventWaiter.workflowStarted();
        refreshEvents();
        return this;
    }

    /**
     * Waits for a workflow completed event before continuing with assertions.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitForWorkflowCompleted() {
        ensureEventWaiterAvailable();
        eventWaiter.workflowCompleted();
        refreshEvents();
        return this;
    }

    /**
     * Waits for a workflow completed event with custom timeout before continuing with assertions.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @param timeout the maximum time to wait
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitForWorkflowCompleted(Duration timeout) {
        ensureEventWaiterAvailable();
        eventWaiter.timeout(timeout).workflowCompleted();
        refreshEvents();
        return this;
    }

    /**
     * Waits for a workflow failed event before continuing with assertions.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitForWorkflowFailed() {
        ensureEventWaiterAvailable();
        eventWaiter.workflowFailed();
        refreshEvents();
        return this;
    }

    /**
     * Waits for a task started event before continuing with assertions.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitForTaskStarted(String taskName) {
        ensureEventWaiterAvailable();
        eventWaiter.taskStarted(taskName);
        refreshEvents();
        return this;
    }

    /**
     * Waits for a task completed event before continuing with assertions.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitForTaskCompleted(String taskName) {
        ensureEventWaiterAvailable();
        eventWaiter.taskCompleted(taskName);
        refreshEvents();
        return this;
    }

    /**
     * Waits for a task failed event before continuing with assertions.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitForTaskFailed(String taskName) {
        ensureEventWaiterAvailable();
        eventWaiter.taskFailed(taskName);
        refreshEvents();
        return this;
    }

    /**
     * Configures the timeout for wait operations.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @param timeout the maximum time to wait
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitTimeout(Duration timeout) {
        ensureEventWaiterAvailable();
        eventWaiter.timeout(timeout);
        return this;
    }

    /**
     * Configures the poll interval for wait operations.
     * Requires that FluentEventAssertions was created with a WorkflowEventStore.
     *
     * @param pollInterval the interval between checks
     * @return this for method chaining
     * @throws IllegalStateException if no EventWaiter is available
     */
    public FluentEventAssertions waitPollInterval(Duration pollInterval) {
        ensureEventWaiterAvailable();
        eventWaiter.pollInterval(pollInterval);
        return this;
    }

    private void ensureEventWaiterAvailable() {
        if (eventWaiter == null) {
            throw new IllegalStateException(
                    "EventWaiter functionality requires FluentEventAssertions to be created with a WorkflowEventStore. " +
                    "Use FluentEventAssertions.assertThat(workflowEventStore) instead of assertThat(events).");
        }
    }

    /**
     * Asserts that a workflow started event exists.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @return this for method chaining
     */
    public FluentEventAssertions workflowStarted() {
        if (strictOrder) {
            assertNextEventType(EventType.WORKFLOW_STARTED);
        } else {
            softAssertions.assertThat(events.stream()
                    .anyMatch(e -> e.getType() == EventType.WORKFLOW_STARTED))
                    .as("At least one WORKFLOW_STARTED event")
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a workflow completed event exists.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @return this for method chaining
     */
    public FluentEventAssertions workflowCompleted(WorkflowInstance instance) {
        if (strictOrder) {
            RecordedWorkflowEvent event = assertNextEventType(EventType.WORKFLOW_COMPLETED);
            Assertions.assertThat(event.getInstanceId())
                    .as("Instance ID for WORKFLOW_COMPLETED event")
                    .isEqualTo(instance.id());
        } else {
            softAssertions.assertThat(events.stream().filter(e -> e.getType() == EventType.WORKFLOW_COMPLETED &&
                    e.getInstanceId().equals(instance.id())).count())
                    .as("At least one WORKFLOW_COMPLETED event for instance '%s'", instance.id())
                    .isGreaterThan(0);
        }
        return this;
    }

    /**
     * Asserts that a workflow completed event exists, regardless of instance.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @return this for method chaining
     */
    public FluentEventAssertions workflowCompleted() {
        if (strictOrder) {
            assertNextEventType(EventType.WORKFLOW_COMPLETED);
        } else {
            softAssertions.assertThat(events.stream()
                    .anyMatch(e -> e.getType() == EventType.WORKFLOW_COMPLETED))
                    .as("At least one WORKFLOW_COMPLETED event")
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a workflow failed event exists.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @return this for method chaining
     */
    public FluentEventAssertions workflowFailed() {
        if (strictOrder) {
            assertNextEventType(EventType.WORKFLOW_FAILED);
        } else {
            softAssertions.assertThat(events.stream()
                    .anyMatch(e -> e.getType() == EventType.WORKFLOW_FAILED))
                    .as("At least one WORKFLOW_FAILED event")
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a workflow cancelled event exists.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @return this for method chaining
     */
    public FluentEventAssertions workflowCancelled() {
        if (strictOrder) {
            assertNextEventType(EventType.WORKFLOW_CANCELLED);
        } else {
            softAssertions.assertThat(events.stream()
                    .anyMatch(e -> e.getType() == EventType.WORKFLOW_CANCELLED))
                    .as("At least one WORKFLOW_CANCELLED event")
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a workflow suspended event exists.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @return this for method chaining
     */
    public FluentEventAssertions workflowSuspended() {
        if (strictOrder) {
            assertNextEventType(EventType.WORKFLOW_SUSPENDED);
        } else {
            softAssertions.assertThat(events.stream()
                    .anyMatch(e -> e.getType() == EventType.WORKFLOW_SUSPENDED))
                    .as("At least one WORKFLOW_SUSPENDED event")
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a workflow resumed event exists.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @return this for method chaining
     */
    public FluentEventAssertions workflowResumed() {
        if (strictOrder) {
            assertNextEventType(EventType.WORKFLOW_RESUMED);
        } else {
            softAssertions.assertThat(events.stream()
                    .anyMatch(e -> e.getType() == EventType.WORKFLOW_RESUMED))
                    .as("At least one WORKFLOW_RESUMED event")
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a task started event exists for the specified task name.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @param taskName the expected task name
     * @return this for method chaining
     */
    public FluentEventAssertions taskStarted(String taskName) {
        if (strictOrder) {
            RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_STARTED);
            Assertions.assertThat(event.getTaskName())
                    .as("Task name for TASK_STARTED event")
                    .hasValue(taskName);
        } else {
            softAssertions.assertThat(events.stream()
                    .filter(e -> e.getType() == EventType.TASK_STARTED)
                    .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                    .anyMatch(event -> event.getType() == EventType.TASK_STARTED))
                    .as("At least one TASK_STARTED event for task '%s'", taskName)
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a task completed event exists for the specified task name.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @param taskName the expected task name
     * @return this for method chaining
     */
    public FluentEventAssertions taskCompleted(String taskName) {
        if (strictOrder) {
            RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_COMPLETED);
            Assertions.assertThat(event.getTaskName())
                    .as("Task name for TASK_COMPLETED event")
                    .hasValue(taskName);
        } else {
            softAssertions.assertThat(events.stream()
                    .filter(e -> e.getType() == EventType.TASK_COMPLETED)
                    .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                    .anyMatch(event -> event.getType() == EventType.TASK_COMPLETED))
                    .as("At least one TASK_COMPLETED event for task '%s'", taskName)
                    .isTrue();
        }
        return this;
    }

    public void assertAll() {
        softAssertions.assertAll();
    }

    /**
     * Asserts that a task failed event exists for the specified task name.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @param taskName the expected task name
     * @return this for method chaining
     */
    public FluentEventAssertions taskFailed(String taskName) {
        if (strictOrder) {
            RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_FAILED);
            Assertions.assertThat(event.getTaskName())
                    .as("Task name for TASK_FAILED event")
                    .hasValue(taskName);
        } else {
            softAssertions.assertThat(events.stream()
                    .filter(e -> e.getType() == EventType.TASK_FAILED)
                    .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                    .anyMatch(event -> event.getType() == EventType.TASK_FAILED))
                    .as("At least one TASK_FAILED event for task '%s'", taskName)
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a task cancelled event exists for the specified task name.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @param taskName the expected task name
     * @return this for method chaining
     */
    public FluentEventAssertions taskCancelled(String taskName) {
        if (strictOrder) {
            RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_CANCELLED);
            Assertions.assertThat(event.getTaskName())
                    .as("Task name for TASK_CANCELLED event")
                    .hasValue(taskName);
        } else {
            softAssertions.assertThat(events.stream()
                    .filter(e -> e.getType() == EventType.TASK_CANCELLED)
                    .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                    .anyMatch(event -> event.getType() == EventType.TASK_CANCELLED))
                    .as("At least one TASK_CANCELLED event for task '%s'", taskName)
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a task suspended event exists for the specified task name.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @param taskName the expected task name
     * @return this for method chaining
     */
    public FluentEventAssertions taskSuspended(String taskName) {
        if (strictOrder) {
            RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_SUSPENDED);
            Assertions.assertThat(event.getTaskName())
                    .as("Task name for TASK_SUSPENDED event")
                    .hasValue(taskName);
        } else {
            softAssertions.assertThat(events.stream()
                    .filter(e -> e.getType() == EventType.TASK_SUSPENDED)
                    .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                    .anyMatch(event -> event.getType() == EventType.TASK_SUSPENDED))
                    .as("At least one TASK_SUSPENDED event for task '%s'", taskName)
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a task resumed event exists for the specified task name.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @param taskName the expected task name
     * @return this for method chaining
     */
    public FluentEventAssertions taskResumed(String taskName) {
        if (strictOrder) {
            RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_RESUMED);
            Assertions.assertThat(event.getTaskName())
                    .as("Task name for TASK_RESUMED event")
                    .hasValue(taskName);
        } else {
            softAssertions.assertThat(events.stream()
                    .filter(e -> e.getType() == EventType.TASK_RESUMED)
                    .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                    .anyMatch(event -> event.getType() == EventType.TASK_RESUMED))
                    .as("At least one TASK_RESUMED event for task '%s'", taskName)
                    .isTrue();
        }
        return this;
    }

    /**
     * Asserts that a task retried event exists for the specified task name.
     * Behavior depends on whether inOrder() was called:
     * - With inOrder(): Verifies the event is at the current position in sequence
     * - Without inOrder(): Verifies the event exists anywhere in the event list
     *
     * @param taskName the expected task name
     * @return this for method chaining
     */
    public FluentEventAssertions taskRetried(String taskName) {
        if (strictOrder) {
            RecordedWorkflowEvent event = assertNextEventType(EventType.TASK_RETRIED);
            Assertions.assertThat(event.getTaskName())
                    .as("Task name for TASK_RETRIED event")
                    .hasValue(taskName);
        } else {
            softAssertions.assertThat(events.stream()
                    .filter(e -> e.getType() == EventType.TASK_RETRIED)
                    .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                    .anyMatch(event -> event.getType() == EventType.TASK_RETRIED))
                    .as("At least one TASK_RETRIED event for task '%s'", taskName)
                    .isTrue();
        }
        return this;
    }

    // Output and Error Assertions

    /**
     * Applies custom assertions on the output of the last verified event.
     * The event must have output (typically WORKFLOW_COMPLETED or TASK_COMPLETED events).
     *
     * @param outputAssertion consumer that performs assertions on the output
     * @return this for method chaining
     */
    public FluentEventAssertions withOutput(Consumer<WorkflowModel> outputAssertion) {
        if (currentIndex == 0) {
            throw new AssertionError("No event has been verified yet. Call an event assertion method first.");
        }
        RecordedWorkflowEvent event = events.get(currentIndex - 1);
        WorkflowModel output = event.getOutput()
                .orElseThrow(() -> new AssertionError(
                        "Event " + event.getType() + " does not have output"));
        outputAssertion.accept(output);
        return this;
    }

    /**
     * Applies custom assertions on the error of the last verified event.
     * The event must have an error (typically WORKFLOW_FAILED or TASK_FAILED events).
     *
     * @param errorAssertion consumer that performs assertions on the error
     * @return this for method chaining
     */
    public FluentEventAssertions withError(Consumer<Throwable> errorAssertion) {
        if (currentIndex == 0) {
            throw new AssertionError("No event has been verified yet. Call an event assertion method first.");
        }
        RecordedWorkflowEvent event = events.get(currentIndex - 1);
        Throwable error = event.getError()
                .orElseThrow(() -> new AssertionError(
                        "Event " + event.getType() + " does not have an error"));
        errorAssertion.accept(error);
        return this;
    }

    // Count Assertions

    /**
     * Asserts the total number of recorded events.
     *
     * @param expected the expected event count
     * @return this for method chaining
     */
    public FluentEventAssertions hasEventCount(int expected) {
        Assertions.assertThat(events)
                .as("Total event count")
                .hasSize(expected);
        return this;
    }

    /**
     * Asserts the number of workflow started events.
     *
     * @param expected the expected count
     * @return this for method chaining
     */
    public FluentEventAssertions hasWorkflowStartedEventCount(int expected) {
        return hasEventTypeCount(EventType.WORKFLOW_STARTED, expected);
    }

    /**
     * Asserts the number of workflow completed events.
     *
     * @param expected the expected count
     * @return this for method chaining
     */
    public FluentEventAssertions hasWorkflowCompletedEventCount(int expected) {
        return hasEventTypeCount(EventType.WORKFLOW_COMPLETED, expected);
    }

    /**
     * Asserts the number of task started events.
     *
     * @param expected the expected count
     * @return this for method chaining
     */
    public FluentEventAssertions hasTaskStartedEventCount(int expected) {
        return hasEventTypeCount(EventType.TASK_STARTED, expected);
    }

    /**
     * Asserts the number of task completed events.
     *
     * @param expected the expected count
     * @return this for method chaining
     */
    public FluentEventAssertions hasTaskCompletedEventCount(int expected) {
        return hasEventTypeCount(EventType.TASK_COMPLETED, expected);
    }

    /**
     * Asserts the number of task failed events.
     *
     * @param expected the expected count
     * @return this for method chaining
     */
    public FluentEventAssertions hasTaskFailedEventCount(int expected) {
        return hasEventTypeCount(EventType.TASK_FAILED, expected);
    }

    /**
     * Asserts the number of events of a specific type.
     *
     * @param type the event type
     * @param expected the expected count
     * @return this for method chaining
     */
    public FluentEventAssertions hasEventTypeCount(EventType type, int expected) {
        long count = events.stream()
                .filter(e -> e.getType() == type)
                .count();
        Assertions.assertThat(count)
                .as("Count of " + type + " events")
                .isEqualTo(expected);
        return this;
    }

    /**
     * Asserts that a task completed before another task.
     *
     * @param firstTask the task that should complete first
     * @param secondTask the task that should complete second
     * @return this for method chaining
     */
    public FluentEventAssertions taskCompletedBefore(String firstTask, String secondTask) {
        RecordedWorkflowEvent firstEvent = events.stream()
                .filter(e -> e.getType() == EventType.TASK_COMPLETED)
                .filter(e -> e.getTaskName().map(firstTask::equals).orElse(false))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No TASK_COMPLETED event found for task: " + firstTask));

        RecordedWorkflowEvent secondEvent = events.stream()
                .filter(e -> e.getType() == EventType.TASK_COMPLETED)
                .filter(e -> e.getTaskName().map(secondTask::equals).orElse(false))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No TASK_COMPLETED event found for task: " + secondTask));

        Assertions.assertThat(firstEvent.getTimestamp())
                .as("Task '%s' should complete before task '%s'", firstTask, secondTask)
                .isBefore(secondEvent.getTimestamp());

        return this;
    }

    /**
     * Asserts that the workflow completed within the specified duration.
     *
     * @param duration the maximum allowed duration
     * @return this for method chaining
     */
    public FluentEventAssertions workflowCompletedWithin(Duration duration) {
        RecordedWorkflowEvent startEvent = events.stream()
                .filter(e -> e.getType() == EventType.WORKFLOW_STARTED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No WORKFLOW_STARTED event found"));

        RecordedWorkflowEvent completedEvent = events.stream()
                .filter(e -> e.getType() == EventType.WORKFLOW_COMPLETED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No WORKFLOW_COMPLETED event found"));

        Duration actualDuration = Duration.between(
                startEvent.getTimestamp(),
                completedEvent.getTimestamp());

        Assertions.assertThat(actualDuration)
                .as("Workflow execution duration")
                .isLessThanOrEqualTo(duration);

        return this;
    }

    /**
     * Asserts that all events belong to the specified workflow instance.
     *
     * @param instanceId the expected instance ID
     * @return this for method chaining
     */
    public FluentEventAssertions allEventsForInstance(String instanceId) {
        Assertions.assertThat(events)
                .as("All events should belong to instance " + instanceId)
                .allMatch(e -> instanceId.equals(e.getInstanceId()));
        return this;
    }

    /**
     * Asserts that all events belong to the specified workflow.
     *
     * @param workflowId the expected workflow ID
     * @return this for method chaining
     */
    public FluentEventAssertions allEventsForWorkflow(String workflowId) {
        Assertions.assertThat(events)
                .as("All events should belong to workflow " + workflowId)
                .allMatch(e -> workflowId.equals(e.getWorkflowId()));
        return this;
    }

    private RecordedWorkflowEvent assertNextEventType(EventType expectedType) {
        if (currentIndex >= events.size()) {
            throw new AssertionError(
                    String.format("Expected event %s at index %d, but only %d events were recorded",
                            expectedType, currentIndex, events.size()));
        }

        RecordedWorkflowEvent event = events.get(currentIndex);
        Assertions.assertThat(event.getType())
                .as("Event type at index %d", currentIndex)
                .isEqualTo(expectedType);

        currentIndex++;
        return event;
    }

    /**
     * Resets the current index to allow re-verification of events from the beginning.
     *
     * @return this for method chaining
     */
    public FluentEventAssertions reset() {
        currentIndex = 0;
        return this;
    }

    /**
     * Returns the current verification index.
     *
     * @return the current index
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Returns the total number of events being verified.
     *
     * @return the event count
     */
    public int getEventCount() {
        return events.size();
    }
}
