package io.quarkiverse.flow.testing;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;

/**
 * Utility for waiting for specific workflow events during asynchronous test execution.
 * Provides a fluent API for waiting on workflow lifecycle events with configurable timeouts.
 * <p>
 * Example usage:
 *
 * <pre>
 * eventRecorder.waitFor()
 *         .timeout(Duration.ofSeconds(10))
 *         .workflowCompleted();
 * </pre>
 */
public class EventWaiter {

    private final WorkflowEventStore eventStore;
    private Duration timeout = Duration.ofSeconds(5);
    private Duration pollInterval = Duration.ofMillis(50);

    public EventWaiter(WorkflowEventStore eventStore) {
        this.eventStore = eventStore;
    }

    /**
     * Sets the timeout for waiting for events.
     *
     * @param timeout the maximum time to wait
     * @return this for method chaining
     */
    public EventWaiter timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the poll interval for checking events.
     *
     * @param pollInterval the interval between checks
     * @return this for method chaining
     */
    public EventWaiter pollInterval(Duration pollInterval) {
        if (pollInterval == null || pollInterval.isNegative()) {
            throw new IllegalArgumentException("Poll interval must be positive");
        }
        this.pollInterval = pollInterval;
        return this;
    }

    // Workflow Event Waiters

    /**
     * Waits for a workflow started event.
     *
     * @return this for method chaining
     */
    public EventWaiter workflowStarted() {
        return waitForEvent(EventType.WORKFLOW_STARTED);
    }

    /**
     * Waits for a workflow completed event.
     *
     * @return this for method chaining
     */
    public EventWaiter workflowCompleted() {
        return waitForEvent(EventType.WORKFLOW_COMPLETED);
    }

    /**
     * Waits for a workflow failed event.
     *
     * @return this for method chaining
     */
    public EventWaiter workflowFailed() {
        return waitForEvent(EventType.WORKFLOW_FAILED);
    }

    /**
     * Waits for a workflow cancelled event.
     *
     * @return this for method chaining
     */
    public EventWaiter workflowCancelled() {
        return waitForEvent(EventType.WORKFLOW_CANCELLED);
    }

    /**
     * Waits for a workflow suspended event.
     *
     * @return this for method chaining
     */
    public EventWaiter workflowSuspended() {
        return waitForEvent(EventType.WORKFLOW_SUSPENDED);
    }

    /**
     * Waits for a workflow resumed event.
     *
     * @return this for method chaining
     */
    public EventWaiter workflowResumed() {
        return waitForEvent(EventType.WORKFLOW_RESUMED);
    }

    // Task Event Waiters

    /**
     * Waits for a task started event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public EventWaiter taskStarted(String taskName) {
        return waitForEvent(EventType.TASK_STARTED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
    }

    /**
     * Waits for a task completed event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public EventWaiter taskCompleted(String taskName) {
        return waitForEvent(EventType.TASK_COMPLETED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
    }

    /**
     * Waits for a task failed event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public EventWaiter taskFailed(String taskName) {
        return waitForEvent(EventType.TASK_FAILED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
    }

    /**
     * Waits for a task cancelled event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public EventWaiter taskCancelled(String taskName) {
        return waitForEvent(EventType.TASK_CANCELLED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
    }

    /**
     * Waits for a task suspended event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public EventWaiter taskSuspended(String taskName) {
        return waitForEvent(EventType.TASK_SUSPENDED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
    }

    /**
     * Waits for a task resumed event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public EventWaiter taskResumed(String taskName) {
        return waitForEvent(EventType.TASK_RESUMED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
    }

    /**
     * Waits for any event of the specified type.
     *
     * @param type the event type to wait for
     * @return this for method chaining
     */
    public EventWaiter eventOfType(EventType type) {
        return waitForEvent(type);
    }

    /**
     * Waits for an event matching the specified condition.
     *
     * @param condition the condition to match
     * @return this for method chaining
     */
    public EventWaiter eventMatching(Predicate<RecordedWorkflowEvent> condition) {
        return waitForEvent(null, condition);
    }

    // Core Waiting Logic
    private EventWaiter waitForEvent(EventType type) {
        return waitForEvent(type, e -> true);
    }

    private EventWaiter waitForEvent(EventType type, Predicate<RecordedWorkflowEvent> condition) {
        Instant deadline = Instant.now().plus(timeout);
        long pollMillis = pollInterval.toMillis();

        while (Instant.now().isBefore(deadline)) {
            Optional<RecordedWorkflowEvent> event = eventStore.getAll().stream()
                    .filter(e -> type == null || e.getType() == type)
                    .filter(condition)
                    .findFirst();

            if (event.isPresent()) {
                return this;
            }

            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for event", e);
            }
        }

        String eventDescription = type != null ? type.toString() : "matching condition";
        throw new AssertionError(
                String.format("Timeout waiting for event %s after %s. Current events: %d",
                        eventDescription, timeout, eventStore.size()));
    }

    /**
     * Waits for multiple events in sequence.
     *
     * @param waiters the sequence of waiters to execute
     * @return this for method chaining
     */
    public EventWaiter sequence(EventWaiter... waiters) {
        for (EventWaiter waiter : waiters) {
            waiter.timeout(this.timeout).pollInterval(this.pollInterval);
        }
        return this;
    }
}
