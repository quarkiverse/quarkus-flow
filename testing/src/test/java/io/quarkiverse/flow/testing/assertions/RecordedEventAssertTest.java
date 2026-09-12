package io.quarkiverse.flow.testing.assertions;

import static io.quarkiverse.flow.testing.assertions.FlowAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

class RecordedEventAssertTest {

    private static RecordedEvent.Builder baseEvent() {
        return RecordedEvent.builder()
                .type(EventType.TASK_COMPLETED)
                .workflowId(WorkflowDefinitionId.fromName("test-workflow"))
                .instanceId("instance-1");
    }

    @Test
    @DisplayName("hasType_passes_when_type_matches")
    void hasType_passes_when_type_matches() {
        RecordedEvent event = baseEvent().type(EventType.WORKFLOW_COMPLETED).build();

        assertThat(event).hasType(EventType.WORKFLOW_COMPLETED);
    }

    @Test
    @DisplayName("hasType_fails_when_type_does_not_match")
    void hasType_fails_when_type_does_not_match() {
        RecordedEvent event = baseEvent().type(EventType.WORKFLOW_STARTED).build();

        assertThatThrownBy(() -> assertThat(event).hasType(EventType.WORKFLOW_COMPLETED))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("WORKFLOW_COMPLETED")
                .hasMessageContaining("WORKFLOW_STARTED");
    }

    @Test
    @DisplayName("hasInstanceId_passes_when_instance_id_matches")
    void hasInstanceId_passes_when_instance_id_matches() {
        RecordedEvent event = baseEvent().instanceId("abc-123").build();

        assertThat(event).hasInstanceId("abc-123");
    }

    @Test
    @DisplayName("hasInstanceId_fails_when_instance_id_does_not_match")
    void hasInstanceId_fails_when_instance_id_does_not_match() {
        RecordedEvent event = baseEvent().instanceId("abc-123").build();

        assertThatThrownBy(() -> assertThat(event).hasInstanceId("other-id"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("other-id")
                .hasMessageContaining("abc-123");
    }

    @Test
    @DisplayName("hasTaskName_passes_when_task_name_matches")
    void hasTaskName_passes_when_task_name_matches() {
        RecordedEvent event = baseEvent().addMetadata("taskName", "sendEmail").build();

        assertThat(event).hasTaskName("sendEmail");
    }

    @Test
    @DisplayName("hasTaskName_fails_when_task_name_does_not_match")
    void hasTaskName_fails_when_task_name_does_not_match() {
        RecordedEvent event = baseEvent().addMetadata("taskName", "sendEmail").build();

        assertThatThrownBy(() -> assertThat(event).hasTaskName("other"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("other")
                .hasMessageContaining("sendEmail");
    }

    @Test
    @DisplayName("hasNoTaskName_passes_when_no_task_name_recorded")
    void hasNoTaskName_passes_when_no_task_name_recorded() {
        RecordedEvent event = baseEvent().build();

        assertThat(event).hasNoTaskName();
    }

    @Test
    @DisplayName("hasNoTaskName_fails_when_task_name_recorded")
    void hasNoTaskName_fails_when_task_name_recorded() {
        RecordedEvent event = baseEvent().addMetadata("taskName", "sendEmail").build();

        assertThatThrownBy(() -> assertThat(event).hasNoTaskName())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("sendEmail");
    }

    @Test
    @DisplayName("hasError_passes_when_error_recorded")
    void hasError_passes_when_error_recorded() {
        RecordedEvent event = baseEvent().addMetadata("error", new RuntimeException("boom")).build();

        assertThat(event).hasError();
    }

    @Test
    @DisplayName("hasError_fails_when_no_error_recorded")
    void hasError_fails_when_no_error_recorded() {
        RecordedEvent event = baseEvent().build();

        assertThatThrownBy(() -> assertThat(event).hasError())
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("hasNoError_passes_when_no_error_recorded")
    void hasNoError_passes_when_no_error_recorded() {
        RecordedEvent event = baseEvent().build();

        assertThat(event).hasNoError();
    }

    @Test
    @DisplayName("hasNoError_fails_when_error_recorded")
    void hasNoError_fails_when_error_recorded() {
        RecordedEvent event = baseEvent().addMetadata("error", new RuntimeException("boom")).build();

        assertThatThrownBy(() -> assertThat(event).hasNoError())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("boom");
    }

    @Test
    @DisplayName("hasErrorMessage_passes_when_error_message_matches")
    void hasErrorMessage_passes_when_error_message_matches() {
        RecordedEvent event = baseEvent().addMetadata("errorMessage", "boom").build();

        assertThat(event).hasErrorMessage("boom");
    }

    @Test
    @DisplayName("hasErrorMessage_fails_when_error_message_does_not_match")
    void hasErrorMessage_fails_when_error_message_does_not_match() {
        RecordedEvent event = baseEvent().addMetadata("errorMessage", "boom").build();

        assertThatThrownBy(() -> assertThat(event).hasErrorMessage("other"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("other")
                .hasMessageContaining("boom");
    }
}