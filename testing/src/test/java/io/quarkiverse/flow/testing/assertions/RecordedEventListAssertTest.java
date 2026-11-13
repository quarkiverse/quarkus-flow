package io.quarkiverse.flow.testing.assertions;

import static io.quarkiverse.flow.testing.assertions.FlowAssertions.assertThat;
import static io.quarkiverse.flow.testing.events.EventType.TASK_COMPLETED;
import static io.quarkiverse.flow.testing.events.EventType.TASK_FAILED;
import static io.quarkiverse.flow.testing.events.EventType.TASK_STARTED;
import static io.quarkiverse.flow.testing.events.EventType.WORKFLOW_COMPLETED;
import static io.quarkiverse.flow.testing.events.EventType.WORKFLOW_STARTED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

class RecordedEventListAssertTest {

    private static final WorkflowDefinitionId WORKFLOW_ID = WorkflowDefinitionId.fromName("test-workflow");

    private static RecordedEvent event(EventType type) {
        return event(type, null);
    }

    private static RecordedEvent event(EventType type, String taskName) {
        RecordedEvent.Builder builder = RecordedEvent.builder()
                .type(type)
                .workflowId(WORKFLOW_ID)
                .instanceId("instance-1");
        if (taskName != null) {
            builder.addMetadata("taskName", taskName);
        }
        return builder.build();
    }

    private static List<RecordedEvent> lifecycleEvents() {
        return List.of(
                event(WORKFLOW_STARTED),
                event(TASK_STARTED, "sendEmail"),
                event(TASK_COMPLETED, "sendEmail"),
                event(WORKFLOW_COMPLETED));
    }

    @Test
    @DisplayName("hasEventType_passes_when_a_matching_event_exists")
    void hasEventType_passes_when_a_matching_event_exists() {
        assertThat(lifecycleEvents()).hasEventType(WORKFLOW_COMPLETED);
    }

    @Test
    @DisplayName("hasEventType_fails_when_no_matching_event_exists")
    void hasEventType_fails_when_no_matching_event_exists() {
        assertThatThrownBy(() -> assertThat(lifecycleEvents()).hasEventType(TASK_FAILED))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("TASK_FAILED");
    }

    @Test
    @DisplayName("doesNotHaveEventType_passes_when_no_matching_event_exists")
    void doesNotHaveEventType_passes_when_no_matching_event_exists() {
        assertThat(lifecycleEvents()).doesNotHaveEventType(TASK_FAILED);
    }

    @Test
    @DisplayName("doesNotHaveEventType_fails_when_a_matching_event_exists")
    void doesNotHaveEventType_fails_when_a_matching_event_exists() {
        assertThatThrownBy(() -> assertThat(lifecycleEvents()).doesNotHaveEventType(WORKFLOW_COMPLETED))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("WORKFLOW_COMPLETED");
    }

    @Test
    @DisplayName("hasEventTypes_passes_when_all_given_types_are_present_in_any_order")
    void hasEventTypes_passes_when_all_given_types_are_present_in_any_order() {
        assertThat(lifecycleEvents()).hasEventTypes(WORKFLOW_COMPLETED, WORKFLOW_STARTED);
    }

    @Test
    @DisplayName("hasEventTypes_fails_when_a_given_type_is_missing")
    void hasEventTypes_fails_when_a_given_type_is_missing() {
        assertThatThrownBy(() -> assertThat(lifecycleEvents()).hasEventTypes(WORKFLOW_STARTED, TASK_FAILED))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("TASK_FAILED");
    }

    @Test
    @DisplayName("hasEventTypesExactly_passes_when_the_sequence_matches_in_order")
    void hasEventTypesExactly_passes_when_the_sequence_matches_in_order() {
        assertThat(lifecycleEvents())
                .hasEventTypesExactly(WORKFLOW_STARTED, TASK_STARTED, TASK_COMPLETED, WORKFLOW_COMPLETED);
    }

    @Test
    @DisplayName("hasEventTypesExactly_fails_when_the_order_does_not_match")
    void hasEventTypesExactly_fails_when_the_order_does_not_match() {
        assertThatThrownBy(() -> assertThat(lifecycleEvents())
                .hasEventTypesExactly(TASK_STARTED, WORKFLOW_STARTED, TASK_COMPLETED, WORKFLOW_COMPLETED))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("hasTaskEvent_passes_when_the_task_and_type_match")
    void hasTaskEvent_passes_when_the_task_and_type_match() {
        assertThat(lifecycleEvents()).hasTaskEvent("sendEmail", TASK_COMPLETED);
    }

    @Test
    @DisplayName("hasTaskEvent_fails_when_no_event_matches_both_task_and_type")
    void hasTaskEvent_fails_when_no_event_matches_both_task_and_type() {
        assertThatThrownBy(() -> assertThat(lifecycleEvents()).hasTaskEvent("sendEmail", TASK_FAILED))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("sendEmail")
                .hasMessageContaining("TASK_FAILED");
    }

    @Test
    @DisplayName("filteredByType_narrows_to_only_matching_events")
    void filteredByType_narrows_to_only_matching_events() {
        assertThat(lifecycleEvents())
                .filteredByType(TASK_COMPLETED)
                .hasSize(1)
                .first()
                .hasTaskName("sendEmail");
    }

    @Test
    @DisplayName("standard_list_assertions_are_inherited")
    void standard_list_assertions_are_inherited() {
        assertThat(lifecycleEvents()).hasSize(4).isNotEmpty();
        assertThat(List.<RecordedEvent> of()).isEmpty();
    }
}