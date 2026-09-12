package io.quarkiverse.flow.testing.assertions;

import java.util.Objects;

import org.assertj.core.api.AbstractAssert;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;

/**
 * AssertJ assertions for a single {@link RecordedEvent}. Usually reached by chaining off a
 * {@link RecordedEventListAssert}, e.g. {@code .first()} or {@code .element(0)}, or directly via
 * {@link FlowAssertions#assertThat(RecordedEvent)}.
 */
public class RecordedEventAssert extends AbstractAssert<RecordedEventAssert, RecordedEvent> {

    public RecordedEventAssert(RecordedEvent actual) {
        super(actual, RecordedEventAssert.class);
    }

    /**
     * Asserts that the event's {@link EventType} equals {@code expected}.
     */
    public RecordedEventAssert hasType(EventType expected) {
        isNotNull();
        if (actual.getType() != expected) {
            failWithMessage("Expected event type to be <%s> but was <%s>", expected, actual.getType());
        }
        return this;
    }

    /**
     * Asserts that the event's workflow instance id equals {@code expected}.
     */
    public RecordedEventAssert hasInstanceId(String expected) {
        isNotNull();
        if (!Objects.equals(actual.getInstanceId(), expected)) {
            failWithMessage("Expected instance id to be <%s> but was <%s>", expected, actual.getInstanceId());
        }
        return this;
    }

    /**
     * Asserts that the event's task name equals {@code expected}. Only task-level events (e.g.
     * {@code TASK_STARTED}/{@code TASK_COMPLETED}) carry a task name.
     */
    public RecordedEventAssert hasTaskName(String expected) {
        isNotNull();
        String actualTaskName = actual.getTaskName().orElse(null);
        if (!Objects.equals(actualTaskName, expected)) {
            failWithMessage("Expected task name to be <%s> but was <%s>", expected, actualTaskName);
        }
        return this;
    }

    /**
     * Asserts that the event has no task name, i.e. it is a workflow-level event.
     */
    public RecordedEventAssert hasNoTaskName() {
        isNotNull();
        actual.getTaskName().ifPresent(
                taskName -> failWithMessage("Expected event not to have a task name but it was <%s>", taskName));
        return this;
    }

    /**
     * Asserts that the event has an associated error, e.g. a {@code WORKFLOW_FAILED} or
     * {@code TASK_FAILED} event.
     */
    public RecordedEventAssert hasError() {
        isNotNull();
        if (actual.getError().isEmpty()) {
            failWithMessage("Expected event to have an error but none was recorded");
        }
        return this;
    }

    /**
     * Asserts that the event has no associated error.
     */
    public RecordedEventAssert hasNoError() {
        isNotNull();
        actual.getError().ifPresent(
                error -> failWithMessage("Expected event not to have an error but found <%s>", error));
        return this;
    }

    /**
     * Asserts that the event's error message equals {@code expected}.
     */
    public RecordedEventAssert hasErrorMessage(String expected) {
        isNotNull();
        String actualMessage = actual.getErrorMessage().orElse(null);
        if (!Objects.equals(actualMessage, expected)) {
            failWithMessage("Expected error message to be <%s> but was <%s>", expected, actualMessage);
        }
        return this;
    }
}