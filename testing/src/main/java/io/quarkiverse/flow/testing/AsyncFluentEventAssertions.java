package io.quarkiverse.flow.testing;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;

/**
 * Asynchronous fluent API for waiting on workflow events and then asserting on them.
 * This class provides methods to wait for specific events with configurable timeouts and polling,
 * and then seamlessly transition to assertions via FluentEventAssertions.
 * <p>
 * Supports two modes:
 * <ul>
 * <li><b>Polling mode (default)</b>: Periodically checks for events until found or timeout</li>
 * <li><b>Streaming mode</b>: Listens to events as they arrive (future enhancement)</li>
 * </ul>
 * <p>
 * Example usage:
 *
 * <pre>
 * // Wait for events, then assert
 * eventStore.waitFor()
 *         .timeout(Duration.ofSeconds(10))
 *         .workflowStarted()
 *         .taskCompleted("task1")
 *         .workflowCompleted()
 *         .thenAssert()
 *         .inOrder()
 *         .workflowStarted()
 *         .taskStarted("task1")
 *         .taskCompleted("task1")
 *         .workflowCompleted()
 *         .assertAll();
 * </pre>
 */
public class AsyncFluentEventAssertions {

    private final WorkflowEventStore eventStore;
    private Duration timeout = Duration.ofSeconds(5);
    private Duration pollInterval = Duration.ofMillis(100);
    private boolean useStreaming = false;
    private String instanceIdFilter = null;

    public AsyncFluentEventAssertions(WorkflowEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore cannot be null");
    }

    // Configuration Methods

    /**
     * Sets the timeout for waiting for events.
     *
     * @param timeout the maximum time to wait
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the poll interval for checking events in polling mode.
     *
     * @param pollInterval the interval between checks
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions pollInterval(Duration pollInterval) {
        if (pollInterval == null || pollInterval.isNegative()) {
            throw new IllegalArgumentException("Poll interval must be positive");
        }
        this.pollInterval = pollInterval;
        return this;
    }

    /**
     * Enables streaming mode where events are processed as they arrive.
     * This is more efficient than polling for long-running workflows.
     * Note: Streaming mode is a future enhancement and currently falls back to polling.
     *
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions streaming() {
        this.useStreaming = true;
        return this;
    }

    /**
     * Enables polling mode (default) where events are checked periodically.
     *
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions polling() {
        this.useStreaming = false;
        return this;
    }

    /**
     * Filters events to only wait for those from the specified workflow instance.
     * This is useful when testing multiple workflow instances and you want to wait
     * for events from a specific instance.
     *
     * @param instanceId the workflow instance ID to filter by
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions forInstance(String instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId cannot be null");
        }
        this.instanceIdFilter = instanceId;
        return this;
    }

    // Workflow Event Waiters

    /**
     * Waits for a workflow started event.
     *
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions workflowStarted() {
        waitForEvent(EventType.WORKFLOW_STARTED, e -> true);
        return this;
    }

    /**
     * Waits for a workflow completed event.
     *
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions workflowCompleted() {
        waitForEvent(EventType.WORKFLOW_COMPLETED, e -> true);
        return this;
    }

    /**
     * Waits for a workflow failed event.
     *
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions workflowFailed() {
        waitForEvent(EventType.WORKFLOW_FAILED, e -> true);
        return this;
    }

    /**
     * Waits for a workflow cancelled event.
     *
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions workflowCancelled() {
        waitForEvent(EventType.WORKFLOW_CANCELLED, e -> true);
        return this;
    }

    /**
     * Waits for a workflow suspended event.
     *
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions workflowSuspended() {
        waitForEvent(EventType.WORKFLOW_SUSPENDED, e -> true);
        return this;
    }

    /**
     * Waits for a workflow resumed event.
     *
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions workflowResumed() {
        waitForEvent(EventType.WORKFLOW_RESUMED, e -> true);
        return this;
    }

    // Task Event Waiters

    /**
     * Waits for a task started event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions taskStarted(String taskName) {
        waitForEvent(EventType.TASK_STARTED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
        return this;
    }

    /**
     * Waits for a task completed event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions taskCompleted(String taskName) {
        waitForEvent(EventType.TASK_COMPLETED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
        return this;
    }

    /**
     * Waits for a task failed event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions taskFailed(String taskName) {
        waitForEvent(EventType.TASK_FAILED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
        return this;
    }

    /**
     * Waits for a task cancelled event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions taskCancelled(String taskName) {
        waitForEvent(EventType.TASK_CANCELLED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
        return this;
    }

    /**
     * Waits for a task suspended event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions taskSuspended(String taskName) {
        waitForEvent(EventType.TASK_SUSPENDED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
        return this;
    }

    /**
     * Waits for a task resumed event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions taskResumed(String taskName) {
        waitForEvent(EventType.TASK_RESUMED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
        return this;
    }

    // Custom Event Waiters

    /**
     * Waits for any event of the specified type.
     *
     * @param type the event type to wait for
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions eventOfType(EventType type) {
        waitForEvent(type, e -> true);
        return this;
    }

    /**
     * Waits for an event matching the specified condition.
     *
     * @param condition the condition to match
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions eventMatching(Predicate<RecordedWorkflowEvent> condition) {
        waitForEvent(null, condition);
        return this;
    }

    /**
     * Waits for an event and applies a consumer to it when found.
     *
     * @param type the event type to wait for
     * @param consumer the consumer to apply to the event
     * @return this for method chaining
     */
    public AsyncFluentEventAssertions eventOfType(EventType type, Consumer<RecordedWorkflowEvent> consumer) {
        RecordedWorkflowEvent event = waitForEventAndReturn(type, e -> true);
        consumer.accept(event);
        return this;
    }

    // Transition to Assertions

    /**
     * Completes the waiting phase and returns an OrderableFluentEventAssertions instance
     * for making assertions on the collected events.
     *
     * @return ConfigurableAssertions for making assertions (allows inOrder())
     */
    public ConfigurableAssertions thenAssert() {
        return FluentEventAssertions.assertThat(eventStore);
    }

    /**
     * Alias for thenAssert() - completes waiting and returns assertions.
     *
     * @return ConfigurableAssertions for making assertions (allows inOrder())
     */
    public ConfigurableAssertions assertThat() {
        return thenAssert();
    }

    // Core Waiting Logic

    private void waitForEvent(EventType type, Predicate<RecordedWorkflowEvent> condition) {
        waitForEventAndReturn(type, condition);
    }

    private RecordedWorkflowEvent waitForEventAndReturn(EventType type, Predicate<RecordedWorkflowEvent> condition) {
        if (useStreaming) {
            return waitForEventStreaming(type, condition);
        } else {
            return waitForEventPolling(type, condition);
        }
    }

    private RecordedWorkflowEvent waitForEventPolling(EventType type, Predicate<RecordedWorkflowEvent> condition) {
        Instant deadline = Instant.now().plus(timeout);
        long pollMillis = pollInterval.toMillis();

        while (Instant.now().isBefore(deadline)) {
            Optional<RecordedWorkflowEvent> event = eventStore.getAll().stream()
                    .filter(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                    .filter(e -> type == null || e.getType() == type)
                    .filter(condition)
                    .findFirst();

            if (event.isPresent()) {
                return event.get();
            }

            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for event", e);
            }
        }

        String eventDescription = type != null ? type.toString() : "matching condition";
        String instanceInfo = instanceIdFilter != null ? " for instance " + instanceIdFilter : "";
        throw new AssertionError(
                String.format("Timeout waiting for event %s%s after %s. Current events: %d",
                        eventDescription, instanceInfo, timeout, eventStore.size()));
    }

    private RecordedWorkflowEvent waitForEventStreaming(EventType type, Predicate<RecordedWorkflowEvent> condition) {
        // TODO: Implement streaming mode using reactive streams or event listeners
        // For now, fall back to polling
        return waitForEventPolling(type, condition);
    }
}
