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
 * Uses ThreadLocal storage to ensure test isolation when tests run in parallel.
 * Events are stored per thread and automatically isolated between test methods.
 */
@ApplicationScoped
public class WorkflowEventStore {

    // Thread-local storage ensures test isolation in parallel execution
    private final ThreadLocal<List<RecordedWorkflowEvent>> events = ThreadLocal
            .withInitial(CopyOnWriteArrayList::new);

    /**
     * Records a workflow event in the thread-local storage.
     *
     * @param event the event to record
     */
    public void record(RecordedWorkflowEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        events.get().add(event);
    }

    /**
     * Returns all recorded events for the current thread.
     *
     * @return immutable list of all recorded events
     */
    public List<RecordedWorkflowEvent> getAll() {
        return new ArrayList<>(events.get());
    }

    /**
     * Returns all events of a specific type for the current thread.
     *
     * @param type the event type to filter by
     * @return immutable list of events matching the type
     */
    public List<RecordedWorkflowEvent> getByType(EventType type) {
        if (type == null) {
            throw new IllegalArgumentException("EventType cannot be null");
        }
        return events.get().stream()
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
            throw new IllegalArgumentException("InstanceId cannot be null");
        }
        return events.get().stream()
                .filter(e -> instanceId.equals(e.getInstanceId()))
                .collect(Collectors.toList());
    }

    /**
     * Returns all workflow-level events (not task events).
     *
     * @return immutable list of workflow events
     */
    public List<RecordedWorkflowEvent> getWorkflowEvents() {
        return events.get().stream()
                .filter(RecordedWorkflowEvent::isWorkflowEvent)
                .collect(Collectors.toList());
    }

    /**
     * Returns all task-level events.
     *
     * @return immutable list of task events
     */
    public List<RecordedWorkflowEvent> getTaskEvents() {
        return events.get().stream()
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
            throw new IllegalArgumentException("TaskName cannot be null");
        }
        return events.get().stream()
                .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                .collect(Collectors.toList());
    }

    /**
     * Clears all recorded events for the current thread.
     * This should be called after each test to ensure test isolation.
     */
    public void clear() {
        events.get().clear();
    }

    /**
     * Returns the number of recorded events for the current thread.
     *
     * @return the event count
     */
    public int size() {
        return events.get().size();
    }

    /**
     * Checks if any events have been recorded for the current thread.
     *
     * @return true if no events recorded, false otherwise
     */
    public boolean isEmpty() {
        return events.get().isEmpty();
    }

    /**
     * Removes the thread-local storage for the current thread.
     * This is useful for cleanup in long-running test scenarios.
     */
    public void remove() {
        events.remove();
    }
}
