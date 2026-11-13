package io.quarkiverse.flow.testing.assertions;

import static io.quarkiverse.flow.testing.events.EventType.TASK_COMPLETED;
import static io.quarkiverse.flow.testing.events.EventType.WORKFLOW_STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

class FlowAssertionsTest {

    private static final WorkflowDefinitionId WORKFLOW_ID = WorkflowDefinitionId.fromName("test-workflow");

    private static RecordedEvent event(EventType type, String instanceId) {
        return RecordedEvent.builder()
                .type(type)
                .workflowId(WORKFLOW_ID)
                .instanceId(instanceId)
                .build();
    }

    @Test
    @DisplayName("assertThat_on_a_list_returns_a_RecordedEventListAssert")
    void assertThat_on_a_list_returns_a_RecordedEventListAssert() {
        List<RecordedEvent> events = List.of(event(TASK_COMPLETED, "instance-1"));

        RecordedEventListAssert result = FlowAssertions.assertThat(events);

        assertThat(result).isNotNull();
        result.hasEventType(TASK_COMPLETED);
    }

    @Test
    @DisplayName("assertThat_on_a_single_event_returns_a_RecordedEventAssert")
    void assertThat_on_a_single_event_returns_a_RecordedEventAssert() {
        RecordedEvent recordedEvent = event(WORKFLOW_STARTED, "instance-1");

        RecordedEventAssert result = FlowAssertions.assertThat(recordedEvent);

        assertThat(result).isNotNull();
        result.hasType(WORKFLOW_STARTED);
    }
}