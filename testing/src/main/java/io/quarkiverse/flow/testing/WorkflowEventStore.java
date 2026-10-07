package io.quarkiverse.flow.testing;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;

/**
 * Buffers every {@link RecordedEvent} emitted while a workflow runs, so tests can assert on the
 * lifecycle after the fact rather than reacting to events as they happen.
 *
 * <p>
 * In a Quarkus test, {@code FlowTestingProducer} produces this as a CDI bean and wires it into a
 * {@code WorkflowExecutionListener} that records every workflow/task event the engine fires. It
 * is a single, shared buffer for the whole test run: {@code @QuarkusFlowTest} clears it
 * automatically at the start of each scoped session, and {@link io.quarkiverse.flow.testing.assertions.FlowAssertions}
 * is the preferred way to query it, but the {@code getByX} methods below are available for
 * assembling assertions manually.
 *
 * <p>
 * Outside of CDI, the no-arg constructor builds a standalone store useful for unit-testing the
 * assertion classes themselves.
 */
public class WorkflowEventStore {

    private final WorkflowEventStorage eventStorage;

    public WorkflowEventStore() {
        this(new DefaultWorkflowEventStorage());
    }

    /**
     * Creates a {@code WorkflowEventStore} backed by the supplied storage strategy.
     *
     * @param eventStorage the storage implementation to delegate to; must not be null
     */
    public WorkflowEventStore(WorkflowEventStorage eventStorage) {
        this.eventStorage = Objects.requireNonNull(eventStorage, "storage must not be null");
    }

    /**
     * Records a workflow event.
     *
     * @param event the event to record; must not be null
     */
    public void record(RecordedEvent event) {
        eventStorage.record(Objects.requireNonNull(event, "'event' must not be null"));
    }

    /**
     * Returns all recorded events.
     *
     * @return immutable list of all recorded events
     */
    public Collection<RecordedEvent> getAll() {
        return eventStorage.getAll();
    }

    /**
     * Returns all events of a specific type.
     *
     * @param type the event type to filter by
     * @return immutable list of events matching the type
     */
    public List<RecordedEvent> getByType(EventType type) {
        Objects.requireNonNull(type, "'type' must not be null");
        return eventStorage.getAll().stream()
                .filter(e -> e.getType() == type)
                .toList();
    }

    /**
     * Returns all events for a specific workflow instance.
     *
     * @param instanceId the workflow instance ID to filter by
     * @return immutable list of events for the instance
     */
    public List<RecordedEvent> getByInstanceId(String instanceId) {
        Objects.requireNonNull(instanceId, "'instanceId' must not be null");
        return eventStorage.getAll().stream()
                .filter(e -> instanceId.equals(e.getInstanceId()))
                .collect(Collectors.toList());
    }

    public List<RecordedEvent> getByTaskName(String taskName) {
        if (taskName == null) {
            throw new IllegalArgumentException("'taskName' must not be null");
        }
        return eventStorage.getAll().stream()
                .filter(e -> e.getTaskName().map(taskName::equals).orElse(false))
                .collect(Collectors.toList());
    }

    /**
     * Discards all recorded events. {@code @QuarkusFlowTest} calls this automatically at the
     * start (and, for {@code PER_CLASS} scope, end) of each recording session, so tests
     * typically don't need to call it directly.
     */
    public void clear() {
        eventStorage.clear();
    }

    /**
     * Returns the number of recorded events.
     *
     * @return the event count
     */
    public int size() {
        return eventStorage.size();
    }

    /**
     * Returns {@code true} if no events have been recorded.
     */
    public boolean isEmpty() {
        return eventStorage.isEmpty();
    }

}
