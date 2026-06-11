package io.quarkiverse.flow.testing;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.quarkiverse.flow.testing.assertions.FlowAssertions;
import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedWorkflowEvent;

public class WorkflowEventStore {

    private final WorkflowEventStorage storage;

    public WorkflowEventStore() {
        this(new DefaultWorkflowEventStorage());
    }

    /**
     * Creates a {@code WorkflowEventStore} backed by the supplied storage strategy.
     *
     * @param storage the storage implementation to delegate to; must not be null
     */
    public WorkflowEventStore(WorkflowEventStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
    }

    public static WorkflowEventStore createInstance() {
        return new WorkflowEventStore();
    }

    /**
     * Records a workflow event.
     *
     * @param event the event to record; must not be null
     */
    public void record(RecordedWorkflowEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        storage.record(event);
    }

    /**
     * Returns all recorded events.
     *
     * @return immutable list of all recorded events
     */
    public List<RecordedWorkflowEvent> getAll() {
        return storage.getAll();
    }

    /**
     * Returns all events of a specific type.
     *
     * @param type the event type to filter by
     * @return immutable list of events matching the type
     */
    public List<RecordedWorkflowEvent> getByType(EventType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return storage.getAll().stream()
                .filter(e -> e.getType() == type)
                .toList();
    }

    /**
     * Returns all events for a specific workflow instance.
     *
     * @param instanceId the workflow instance ID to filter by
     * @return immutable list of events for the instance
     */
    public List<RecordedWorkflowEvent> getByInstanceId(String instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId must not be null");
        }
        return storage.getAll().stream()
                .filter(e -> instanceId.equals(e.getInstanceId()))
                .collect(Collectors.toList());
    }

    /**
     * Returns all events for a specific workflow instance.
     * Alias for {@link #getByInstanceId(String)} intended for use with
     * {@link FlowAssertions#assertWith(List)}.
     *
     * @param instanceId the workflow instance ID to filter by
     * @return immutable list of events for the instance
     */
    public List<RecordedWorkflowEvent> filterByInstanceId(String instanceId) {
        return getByInstanceId(instanceId);
    }

    public List<RecordedWorkflowEvent> getByTaskName(String taskName) {
        if (taskName == null) {
            throw new IllegalArgumentException("taskName must not be null");
        }
        return storage.getAll().stream()
                .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                .collect(Collectors.toList());
    }

    public void clear() {
        storage.clear();
    }

    /**
     * Returns the number of recorded events.
     *
     * @return the event count
     */
    public int size() {
        return storage.size();
    }

    /**
     * Returns {@code true} if no events have been recorded.
     */
    public boolean isEmpty() {
        return storage.isEmpty();
    }

}
