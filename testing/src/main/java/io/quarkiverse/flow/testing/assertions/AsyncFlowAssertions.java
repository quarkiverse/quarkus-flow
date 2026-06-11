package io.quarkiverse.flow.testing.assertions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.assertj.core.api.Assertions;

import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;
import io.serverlessworkflow.impl.WorkflowModel;

public class AsyncFlowAssertions implements ConfigurableAssertions {

    private final WorkflowEventStore eventStore;
    private Duration timeout = Duration.ofSeconds(5);
    private Duration pollInterval = Duration.ofMillis(100);
    private String instanceIdFilter = null;
    private RecordedWorkflowEvent lastWaitedEvent = null;
    private boolean strictlyOrdered = false;
    private int lastMatchedIndex = -1;

    private AsyncFlowAssertions(WorkflowEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore cannot be null");
    }

    public static AsyncFlowAssertions assertWith(WorkflowEventStore store) {
        return new AsyncFlowAssertions(store);
    }

    /**
     * Transitions from async waiting to synchronous fluent assertions over the events
     * recorded so far in the store.
     * <p>
     * Call this after all required events have been waited for, then chain
     * {@link ConfigurableAssertions} methods to make structural assertions.
     *
     * <pre>
     * AsyncFlowAssertions.assertWith(store)
     *         .workflowCompleted()
     *         .andAssert()
     *         .strictly()
     *         .workflowStarted()
     *         .workflowCompleted();
     * </pre>
     *
     * @return a {@link ConfigurableAssertions} backed by the current snapshot of the store
     */
    public ConfigurableAssertions andAssert() {
        return AsyncFlowAssertions.assertWith(eventStore);
    }

    public AsyncFlowAssertions timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    public AsyncFlowAssertions pollInterval(Duration pollInterval) {
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("Poll interval must be positive");
        }
        this.pollInterval = pollInterval;
        return this;
    }

    public AsyncFlowAssertions workflowStarted() {
        waitForEvent(EventType.WORKFLOW_STARTED, e -> true);
        return this;
    }

    @Override
    public AsyncFlowAssertions workflowCompleted() {
        waitForEvent(EventType.WORKFLOW_COMPLETED, e -> true);
        return this;
    }

    @Override
    public WorkflowAssertions hasWorkflowStartedEventCount(int expected) {
        Assertions.assertThat(waitForEventsPolling(EventType.WORKFLOW_STARTED)
                .stream()
                .filter(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                .count())
                .isEqualTo(expected);
        return this;
    }

    @Override
    public WorkflowAssertions hasWorkflowCompletedEventCount(int expected) {
        Assertions.assertThat(waitForEventsPolling(EventType.WORKFLOW_COMPLETED)
                .stream()
                .filter(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                .count())
                .isEqualTo(expected);
        return this;
    }

    @Override
    public WorkflowAssertions hasWorkflowFailedEventCount(int expected) {
        Assertions.assertThat(waitForEventsPolling(EventType.WORKFLOW_FAILED)
                .stream()
                .filter(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                .count())
                .as("Expected %d WORKFLOW_FAILED events", expected)
                .isEqualTo(expected);
        return this;
    }

    @Override
    public WorkflowAssertions hasWorkflowCanceledEventCount(int expected) {
        Assertions.assertThat(waitForEventsPolling(EventType.WORKFLOW_CANCELED)
                .stream()
                .filter(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                .count())
                .as("Expected %d WORKFLOW_CANCELED events", expected)
                .isEqualTo(expected);
        return this;
    }

    @Override
    public WorkflowAssertions hasWorkflowSuspendedEventCount(int expected) {
        Assertions.assertThat(waitForEventsPolling(EventType.WORKFLOW_SUSPENDED)
                .stream()
                .filter(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                .count())
                .as("Expected %d WORKFLOW_SUSPENDED events", expected)
                .isEqualTo(expected);
        return this;
    }

    @Override
    public WorkflowAssertions hasWorkflowResumedEventCount(int expected) {
        Assertions.assertThat(waitForEventsPolling(EventType.WORKFLOW_RESUMED)
                .stream()
                .filter(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                .count())
                .as("Expected %d WORKFLOW_RESUMED events", expected)
                .isEqualTo(expected);
        return this;
    }

    @Override
    public WorkflowAssertions hasTaskStartedEventCount(int expected) {
        List<RecordedWorkflowEvent> allSatisfied = waitForEventsPolling(EventType.TASK_STARTED);
        Assertions.assertThat(allSatisfied)
                .as("Expected %d TASK_COMPLETED events but found %d.", expected, allSatisfied.size())
                .hasSize(expected);
        return this;
    }

    @Override
    public WorkflowAssertions hasTaskCompletedEventCount(int expected) {
        List<RecordedWorkflowEvent> allSatisfied = waitForEventsPolling(EventType.TASK_COMPLETED);
        Assertions.assertThat(allSatisfied)
                .as("Expected %d TASK_COMPLETED events but found %d.", expected, allSatisfied.size())
                .hasSize(expected);
        return this;
    }

    @Override
    public WorkflowAssertions workflowCompletedWithin(Duration duration) {
        workflowStarted();
        workflowCompleted();
        return andAssert().workflowCompletedWithin(duration);
    }

    @Override
    public WorkflowAssertions allEventsForInstance(String id) {
        return andAssert().allEventsForInstance(id);
    }

    @Override
    public TaskCompletionOrderAssertions assertTask(String taskName) {
        taskCompleted(taskName);
        return andAssert().assertTask(taskName);
    }

    @Override
    public void withOutput(Consumer<WorkflowModel> outputAssertion) {
        if (lastWaitedEvent == null) {
            throw new AssertionError("No event has been waited for yet. Call an event wait method first.");
        }
        WorkflowModel output = lastWaitedEvent.getOutput()
                .orElseThrow(() -> new AssertionError(
                        "Event " + lastWaitedEvent.getType() + " does not have output"));
        outputAssertion.accept(output);
    }

    @Override
    public ConfigurableAssertions configure() {
        return andAssert();
    }

    /**
     * Waits for a workflow failed event.
     *
     * @return this for method chaining
     */
    public AsyncFlowAssertions workflowFailed() {
        waitForEvent(EventType.WORKFLOW_FAILED, e -> true);
        return this;
    }

    /**
     * Waits for a workflow cancelled event.
     *
     * @return this for method chaining
     */
    public AsyncFlowAssertions workflowCancelled() {
        waitForEvent(EventType.WORKFLOW_CANCELED, e -> true);
        return this;
    }

    /**
     * Waits for a workflow suspended event.
     *
     * @return this for method chaining
     */
    public AsyncFlowAssertions workflowSuspended() {
        waitForEvent(EventType.WORKFLOW_SUSPENDED, e -> true);
        return this;
    }

    /**
     * Waits for a workflow resumed event.
     *
     * @return this for method chaining
     */
    public AsyncFlowAssertions workflowResumed() {
        waitForEvent(EventType.WORKFLOW_RESUMED, e -> true);
        return this;
    }

    /**
     * Waits for a task started event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public AsyncFlowAssertions taskStarted(String taskName) {
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
    public AsyncFlowAssertions taskCompleted(String taskName) {
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
    public AsyncFlowAssertions taskFailed(String taskName) {
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
    public AsyncFlowAssertions taskCancelled(String taskName) {
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
    public AsyncFlowAssertions taskSuspended(String taskName) {
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
    public AsyncFlowAssertions taskResumed(String taskName) {
        waitForEvent(EventType.TASK_RESUMED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
        return this;
    }

    /**
     * Waits for a task retried event with the specified task name.
     *
     * @param taskName the task name to wait for
     * @return this for method chaining
     */
    public AsyncFlowAssertions taskRetried(String taskName) {
        waitForEvent(EventType.TASK_RETRIED,
                e -> e.getTaskName().map(taskName::equals).orElse(false));
        return this;
    }

    private void waitForEvent(EventType type, Predicate<RecordedWorkflowEvent> condition) {
        Instant deadline = Instant.now().plus(timeout);
        long pollMillis = pollInterval.toMillis();

        while (Instant.now().isBefore(deadline)) {
            List<RecordedWorkflowEvent> all = eventStore.getAll();

            if (strictlyOrdered) {
                // Only look at events that come after the last matched position
                for (int i = lastMatchedIndex + 1; i < all.size(); i++) {
                    RecordedWorkflowEvent e = all.get(i);
                    if ((instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                            && (type == null || e.getType() == type)
                            && condition.test(e)) {
                        lastMatchedIndex = i;
                        lastWaitedEvent = e;
                        return;
                    }
                }
            } else {
                Optional<RecordedWorkflowEvent> event = all.stream()
                        .filter(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                        .filter(e -> type == null || e.getType() == type)
                        .filter(condition)
                        .findFirst();

                if (event.isPresent()) {
                    lastWaitedEvent = event.get();
                    return;
                }
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
        String orderInfo = strictlyOrdered ? " (strictly after position " + lastMatchedIndex + ")" : "";
        throw new AssertionError(
                String.format("Timeout waiting for event %s%s%s after %s. Current events: %d",
                        eventDescription, instanceInfo, orderInfo, timeout, eventStore.size()));
    }

    private List<RecordedWorkflowEvent> waitForEventsPolling(EventType type) {
        Predicate<RecordedWorkflowEvent> composedFilter = buildFilter(type);
        Instant deadline = Instant.now().plus(timeout);

        while (true) {
            List<RecordedWorkflowEvent> results = eventStore.getAll().stream()
                    .filter(composedFilter)
                    .toList();

            if (!results.isEmpty()) {
                return results;
            }

            if (Instant.now().isAfter(deadline)) {
                return List.of();
            }

            LockSupport.parkNanos(pollInterval.toMillis() * 1_000_000L);
        }
    }

    private Predicate<RecordedWorkflowEvent> buildFilter(EventType type) {
        Predicate<RecordedWorkflowEvent> filter = event -> true;
        return filter.and(e -> instanceIdFilter == null || instanceIdFilter.equals(e.getInstanceId()))
                .and(e -> type == null || e.getType() == type);
    }

    /**
     * Enables strict ordering for subsequent event waits.
     * When active, each event is only matched if it appears <em>after</em> the position
     * of the previously matched event in the store, enforcing the declared order.
     *
     * @return this for method chaining
     */
    @Override
    public ConfigurableAssertions strictly() {
        this.strictlyOrdered = true;
        return this;
    }

    /**
     * Restricts all subsequent event waits to the given workflow instance.
     *
     * @param instanceId the workflow instance ID to filter by; must not be null
     * @return this for method chaining
     */
    @Override
    public ConfigurableAssertions filteringBy(String instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId cannot be null");
        }
        this.instanceIdFilter = instanceId;
        return this;
    }

    /**
     * Resets all mutable state — instance filter, last waited event, timeout, and poll interval —
     * back to their defaults so this instance can be reused for a new assertion chain.
     *
     * @return this for method chaining
     */
    @Override
    public AsyncFlowAssertions reset() {
        this.instanceIdFilter = null;
        this.lastWaitedEvent = null;
        this.strictlyOrdered = false;
        this.lastMatchedIndex = -1;
        this.timeout = Duration.ofSeconds(5);
        this.pollInterval = Duration.ofMillis(100);
        return this;
    }
}
