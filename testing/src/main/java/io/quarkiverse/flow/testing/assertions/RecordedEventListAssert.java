package io.quarkiverse.flow.testing.assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.AbstractListAssert;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;

/**
 * AssertJ assertions for a filtered list of {@link RecordedEvent}s, as returned by
 * {@link WorkflowEventStoreAssert}'s {@code filteredByX} methods,
 * {@link WorkflowInstanceAssert#events()}, or directly via
 * {@link FlowAssertions#assertThat(java.util.List)}.
 */
public class RecordedEventListAssert
        extends AbstractListAssert<RecordedEventListAssert, List<RecordedEvent>, RecordedEvent, RecordedEventAssert> {

    public RecordedEventListAssert(List<RecordedEvent> actual) {
        super(actual, RecordedEventListAssert.class);
    }

    @Override
    protected RecordedEventAssert toAssert(RecordedEvent value, String description) {
        return new RecordedEventAssert(value).as(description);
    }

    @Override
    protected RecordedEventListAssert newAbstractIterableAssert(Iterable<? extends RecordedEvent> iterable) {
        List<RecordedEvent> list = new ArrayList<>();
        iterable.forEach(list::add);
        return new RecordedEventListAssert(list);
    }

    /**
     * Asserts that at least one recorded event has the given type.
     */
    public RecordedEventListAssert hasEventType(EventType expected) {
        isNotNull();
        boolean found = actual.stream().anyMatch(e -> e.getType() == expected);
        if (!found) {
            failWithMessage("Expected events to contain an event of type <%s> but recorded types were <%s>",
                    expected, recordedTypes());
        }
        return this;
    }

    /**
     * Asserts that no recorded event has the given type.
     */
    public RecordedEventListAssert doesNotHaveEventType(EventType type) {
        isNotNull();
        boolean found = actual.stream().anyMatch(e -> e.getType() == type);
        if (found) {
            failWithMessage("Expected events not to contain an event of type <%s> but recorded types were <%s>",
                    type, recordedTypes());
        }
        return this;
    }

    /**
     * Asserts that the recorded events contain all the given types, in any order/position.
     */
    public RecordedEventListAssert hasEventTypes(EventType... expected) {
        isNotNull();
        List<EventType> recorded = recordedTypes();
        List<EventType> missing = Arrays.stream(expected)
                .filter(type -> !recorded.contains(type))
                .toList();
        if (!missing.isEmpty()) {
            failWithMessage("Expected events to contain types <%s> but was missing <%s>; recorded types were <%s>",
                    Arrays.asList(expected), missing, recorded);
        }
        return this;
    }

    /**
     * Asserts that the recorded events' types, in recording order, equal the given sequence exactly.
     */
    public RecordedEventListAssert hasEventTypesExactly(EventType... expected) {
        isNotNull();
        List<EventType> recorded = recordedTypes();
        List<EventType> expectedList = Arrays.asList(expected);
        if (!recorded.equals(expectedList)) {
            failWithMessage("Expected event types to be exactly <%s> in order but were <%s>", expectedList, recorded);
        }
        return this;
    }

    /**
     * Asserts that at least one recorded event has the given task name and type.
     */
    public RecordedEventListAssert hasTaskEvent(String taskName, EventType type) {
        isNotNull();
        boolean found = actual.stream()
                .anyMatch(e -> e.getType() == type && e.getTaskName().map(taskName::equals).orElse(false));
        if (!found) {
            failWithMessage("Expected events to contain a <%s> event for task <%s> but recorded events were <%s>",
                    type, taskName, actual);
        }
        return this;
    }

    /**
     * Narrows this list further to only the events of the given type.
     */
    public RecordedEventListAssert filteredByType(EventType type) {
        isNotNull();
        List<RecordedEvent> filtered = actual.stream().filter(e -> e.getType() == type).toList();
        return new RecordedEventListAssert(filtered).as("events of type <%s>", type);
    }

    private List<EventType> recordedTypes() {
        return actual.stream().map(RecordedEvent::getType).toList();
    }
}