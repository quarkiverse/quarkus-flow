package io.quarkiverse.flow.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;

/**
 * Thread-safe storage for workflow events recorded during test execution.
 * <p>
 * Supports two modes:
 * <ul>
 * <li><b>ThreadLocal mode (default)</b>: Uses ThreadLocal storage for test isolation in parallel execution</li>
 * <li><b>Shared mode</b>: Uses a shared list for cross-thread event recording (needed for async workflows)</li>
 * </ul>
 * <p>
 * Use shared mode when testing asynchronous workflows that run in different threads.
 */
@ApplicationScoped
public class WorkflowEventStore {

    // Thread-local storage ensures test isolation in parallel execution
    private final ThreadLocal<List<RecordedWorkflowEvent>> threadLocalEvents = ThreadLocal
            .withInitial(CopyOnWriteArrayList::new);

    // Shared storage for cross-thread scenarios (async workflows)
    private final List<RecordedWorkflowEvent> sharedEvents = new CopyOnWriteArrayList<>();

    // Flag to determine which storage to use
    private final boolean useSharedStorage;

    /**
     * Creates a WorkflowEventStore with ThreadLocal storage (default).
     */
    public WorkflowEventStore() {
        this(false);
    }

    /**
     * Creates a WorkflowEventStore with specified storage mode.
     *
     * @param useSharedStorage if true, uses shared storage for cross-thread access;
     *        if false, uses ThreadLocal storage for test isolation
     */
    public WorkflowEventStore(boolean useSharedStorage) {
        this.useSharedStorage = useSharedStorage;
    }

    /**
     * Records a workflow event in the storage.
     *
     * @param event the event to record
     */
    public void record(RecordedWorkflowEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        if (useSharedStorage) {
            sharedEvents.add(event);
        } else {
            threadLocalEvents.get().add(event);
        }
    }

    /**
     * Returns all recorded events.
     *
     * @return immutable list of all recorded events
     */
    public List<RecordedWorkflowEvent> getAll() {
        if (useSharedStorage) {
            return new ArrayList<>(sharedEvents);
        } else {
            return new ArrayList<>(threadLocalEvents.get());
        }
    }

    /**
     * Returns all events of a specific type.
     *
     * @param type the event type to filter by
     * @return immutable list of events matching the type
     */
    public List<RecordedWorkflowEvent> getByType(EventType type) {
        if (type == null) {
            throw new IllegalArgumentException("EventType cannot be null");
        }
        List<RecordedWorkflowEvent> allEvents = useSharedStorage ? sharedEvents : threadLocalEvents.get();
        return allEvents.stream()
                .filter(e -> e.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Returns all events for a specific workflow instance.
     *
     * @param instanceId the workflow instance ID
     * @return immutable list of events for the instance
     */
    public List<RecordedWorkflowEvent> getByInstanceId(String instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("InstanceId must not be null");
        }
        List<RecordedWorkflowEvent> allEvents = useSharedStorage ? sharedEvents : threadLocalEvents.get();
        return allEvents.stream()
                .filter(e -> instanceId.equals(e.getInstanceId()))
                .collect(Collectors.toList());
    }

    /**
     * Returns all events for a specific workflow instance.
     * Alias for getByInstanceId() for better API consistency.
     *
     * @param instanceId the workflow instance ID
     * @return immutable list of events for the instance
     */
    public List<RecordedWorkflowEvent> getEventsForInstance(String instanceId) {
        return getByInstanceId(instanceId);
    }

    /**
     * Returns all workflow-level events (not task events).
     *
     * @return immutable list of workflow events
     */
    public List<RecordedWorkflowEvent> getWorkflowEvents() {
        List<RecordedWorkflowEvent> allEvents = useSharedStorage ? sharedEvents : threadLocalEvents.get();
        return allEvents.stream()
                .filter(RecordedWorkflowEvent::isWorkflowEvent)
                .collect(Collectors.toList());
    }

    /**
     * Returns all task-level events.
     *
     * @return immutable list of task events
     */
    public List<RecordedWorkflowEvent> getTaskEvents() {
        List<RecordedWorkflowEvent> allEvents = useSharedStorage ? sharedEvents : threadLocalEvents.get();
        return allEvents.stream()
                .filter(RecordedWorkflowEvent::isTaskEvent)
                .collect(Collectors.toList());
    }

    /**
     * Returns all events for a specific task name.
     *
     * @param taskName the task name to filter by
     * @return immutable list of events for the task
     */
    public List<RecordedWorkflowEvent> getByTaskName(String taskName) {
        if (taskName == null) {
            throw new IllegalArgumentException("taskName must not be null");
        }
        List<RecordedWorkflowEvent> allEvents = useSharedStorage ? sharedEvents : threadLocalEvents.get();
        return allEvents.stream()
                .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                .collect(Collectors.toList());
    }

    /**
     * Clears all recorded events.
     * This should be called after each test to ensure test isolation.
     */
    public void clear() {
        if (useSharedStorage) {
            sharedEvents.clear();
        } else {
            threadLocalEvents.get().clear();
        }
    }

    /**
     * Returns the number of recorded events.
     *
     * @return the event count
     */
    public int size() {
        return useSharedStorage ? sharedEvents.size() : threadLocalEvents.get().size();
    }

    /**
     * Checks if any events have been recorded.
     *
     * @return true if no events recorded, false otherwise
     */
    public boolean isEmpty() {
        return useSharedStorage ? sharedEvents.isEmpty() : threadLocalEvents.get().isEmpty();
    }

    /**
     * Removes the thread-local storage for the current thread.
     * Only applicable when using ThreadLocal mode.
     * This is useful for cleanup in long-running test scenarios.
     */
    public void remove() {
        if (!useSharedStorage) {
            threadLocalEvents.remove();
        }
    }

    /**
     * Creates an AsyncFluentEventAssertions instance for waiting on events
     * before making assertions. This is the entry point for async event waiting.
     * <p>
     * Example usage:
     *
     * <pre>
     * eventStore.waitFor()
     *         .timeout(Duration.ofSeconds(10))
     *         .workflowCompleted()
     *         .taskCompleted("task1")
     *         .thenAssert()
     *         .inOrder()
     *         .workflowStarted()
     *         .taskStarted("task1")
     *         .taskCompleted("task1")
     *         .workflowCompleted()
     *         .assertAll();
     * </pre>
     *
     * @return AsyncFluentEventAssertions for waiting on events
     */
    public AsyncFluentEventAssertions waitFor() {
        return new AsyncFluentEventAssertions(this);
    }
}
