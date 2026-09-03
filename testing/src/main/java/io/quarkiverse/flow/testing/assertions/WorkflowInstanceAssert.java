package io.quarkiverse.flow.testing.assertions;

import java.util.List;

import org.assertj.core.api.AbstractAssert;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * AssertJ assertions for a {@link WorkflowInstance}, combining assertions on the instance's own
 * state (e.g. {@link #hasStatus(WorkflowStatus)}) with assertions on the events recorded for it.
 *
 * <p>
 * The recorded events are captured as a snapshot at construction time, so assertions here always
 * see the instance's events as they were when {@link FlowAssertions#assertThat(WorkflowInstance)}
 * was called, even if more events are recorded on the underlying store afterwards.
 *
 * <p>
 * Usually obtained via {@link FlowAssertions#assertThat(WorkflowInstance)}, which resolves the
 * CDI-managed store for you. The public constructor is available directly for unit tests that
 * already have both the instance and its events in hand.
 */
public class WorkflowInstanceAssert extends AbstractAssert<WorkflowInstanceAssert, WorkflowInstance> {

    private final RecordedEventListAssert events;

    public WorkflowInstanceAssert(WorkflowInstance actual, List<RecordedEvent> recordedEvents) {
        super(actual, WorkflowInstanceAssert.class);
        this.events = new RecordedEventListAssert(List.copyOf(recordedEvents));
    }

    /**
     * Asserts that the instance's {@link WorkflowStatus} equals {@code expected}.
     */
    public WorkflowInstanceAssert hasStatus(WorkflowStatus expected) {
        isNotNull();
        if (actual.status() != expected) {
            failWithMessage("Expected workflow instance status to be <%s> but was <%s>", expected, actual.status());
        }
        return this;
    }

    /**
     * Asserts that the instance's id equals {@code expected}.
     */
    public WorkflowInstanceAssert hasId(String expected) {
        isNotNull();
        if (!actual.id().equals(expected)) {
            failWithMessage("Expected workflow instance id to be <%s> but was <%s>", expected, actual.id());
        }
        return this;
    }

    /**
     * Asserts that at least one of this instance's recorded events has the given type.
     */
    public WorkflowInstanceAssert hasEventType(EventType expected) {
        isNotNull();
        events.hasEventType(expected);
        return this;
    }

    /**
     * Asserts that none of this instance's recorded events has the given type.
     */
    public WorkflowInstanceAssert doesNotHaveEventType(EventType type) {
        isNotNull();
        events.doesNotHaveEventType(type);
        return this;
    }

    /**
     * Asserts that this instance's recorded events contain all the given types, in any
     * order/position.
     */
    public WorkflowInstanceAssert hasEventTypes(EventType... expected) {
        isNotNull();
        events.hasEventTypes(expected);
        return this;
    }

    /**
     * Asserts that this instance's recorded events' types, in recording order, equal the given
     * sequence exactly.
     */
    public WorkflowInstanceAssert hasEventTypesExactly(EventType... expected) {
        isNotNull();
        events.hasEventTypesExactly(expected);
        return this;
    }

    /**
     * Asserts that at least one of this instance's recorded events has the given task name and
     * type.
     */
    public WorkflowInstanceAssert hasTaskEvent(String taskName, EventType type) {
        isNotNull();
        events.hasTaskEvent(taskName, type);
        return this;
    }

    /**
     * Returns this instance's recorded events snapshot as a {@link RecordedEventListAssert}, for
     * assertions beyond the ones delegated above (e.g. {@code extracting(...)}, {@code first()}).
     */
    public RecordedEventListAssert events() {
        isNotNull();
        return events;
    }
}
