package io.quarkiverse.flow.testing.assertions;

import org.assertj.core.api.AbstractAssert;

import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkiverse.flow.testing.events.EventType;

/**
 * AssertJ assertions for a {@link WorkflowEventStore}. Filtering methods mirror the store's own
 * query methods and hand off to a {@link RecordedEventListAssert} for further, list-level
 * assertions.
 *
 * <p>
 * Usually obtained via {@link FlowAssertions#assertThat()}, which resolves the CDI-managed store
 * for you. The public constructor is available directly for unit tests that build a standalone
 * {@link WorkflowEventStore} outside of a CDI container.
 */
public class WorkflowEventStoreAssert extends AbstractAssert<WorkflowEventStoreAssert, WorkflowEventStore> {

    public WorkflowEventStoreAssert(WorkflowEventStore actual) {
        super(actual, WorkflowEventStoreAssert.class);
    }

    /**
     * Asserts that no events have been recorded.
     */
    public WorkflowEventStoreAssert isEmpty() {
        isNotNull();
        if (!actual.isEmpty()) {
            failWithMessage("Expected event store to be empty but it recorded <%s> event(s): <%s>",
                    actual.size(), actual.getAll());
        }
        return this;
    }

    /**
     * Asserts that at least one event has been recorded.
     */
    public WorkflowEventStoreAssert isNotEmpty() {
        isNotNull();
        if (actual.isEmpty()) {
            failWithMessage("Expected event store not to be empty but it recorded no events");
        }
        return this;
    }

    /**
     * Asserts that exactly {@code expected} events have been recorded, across all instances.
     */
    public WorkflowEventStoreAssert hasSize(int expected) {
        isNotNull();
        if (actual.size() != expected) {
            failWithMessage("Expected event store to have <%s> event(s) but had <%s>: <%s>",
                    expected, actual.size(), actual.getAll());
        }
        return this;
    }

    /**
     * Filters to the events belonging to the given workflow instance. Does not assert anything by
     * itself: an unknown instance id yields an empty {@link RecordedEventListAssert}, matching
     * {@link WorkflowEventStore#getByInstanceId(String)}'s own behavior.
     */
    public RecordedEventListAssert filteredByInstanceId(String instanceId) {
        isNotNull();
        return new RecordedEventListAssert(actual.getByInstanceId(instanceId))
                .as("events for instance <%s>", instanceId);
    }

    /**
     * Filters to the events of the given type. Does not assert anything by itself.
     */
    public RecordedEventListAssert filteredByType(EventType type) {
        isNotNull();
        return new RecordedEventListAssert(actual.getByType(type))
                .as("events of type <%s>", type);
    }

    /**
     * Filters to the events for the given task name. Does not assert anything by itself.
     */
    public RecordedEventListAssert filteredByTaskName(String taskName) {
        isNotNull();
        return new RecordedEventListAssert(actual.getByTaskName(taskName))
                .as("events for task <%s>", taskName);
    }

    /**
     * Returns every recorded event, across all instances, as a {@link RecordedEventListAssert}.
     * Does not assert anything by itself.
     */
    public RecordedEventListAssert all() {
        isNotNull();
        return new RecordedEventListAssert(actual.getAll().stream().toList())
                .as("all events");
    }
}