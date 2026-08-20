package io.quarkiverse.flow.testing.assertions;

import static io.quarkiverse.flow.testing.events.EventType.TASK_COMPLETED;
import static io.quarkiverse.flow.testing.events.EventType.WORKFLOW_COMPLETED;
import static io.quarkiverse.flow.testing.events.EventType.WORKFLOW_STARTED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkiverse.flow.testing.events.EventType;
import io.quarkiverse.flow.testing.events.RecordedEvent;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

class WorkflowEventStoreAssertTest {

    private static final WorkflowDefinitionId WORKFLOW_ID = WorkflowDefinitionId.fromName("test-workflow");

    private static RecordedEvent event(EventType type, String instanceId) {
        return event(type, instanceId, null);
    }

    private static RecordedEvent event(EventType type, String instanceId, String taskName) {
        RecordedEvent.Builder builder = RecordedEvent.builder()
                .type(type)
                .workflowId(WORKFLOW_ID)
                .instanceId(instanceId);
        if (taskName != null) {
            builder.addMetadata("taskName", taskName);
        }
        return builder.build();
    }

    @Test
    @DisplayName("isEmpty_passes_when_no_events_recorded")
    void isEmpty_passes_when_no_events_recorded() {
        WorkflowEventStore store = new WorkflowEventStore();

        new WorkflowEventStoreAssert(store).isEmpty();
    }

    @Test
    @DisplayName("isEmpty_fails_when_events_recorded")
    void isEmpty_fails_when_events_recorded() {
        WorkflowEventStore store = new WorkflowEventStore();
        store.record(event(WORKFLOW_STARTED, "instance-1"));

        assertThatThrownBy(() -> new WorkflowEventStoreAssert(store).isEmpty())
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("isNotEmpty_passes_when_events_recorded")
    void isNotEmpty_passes_when_events_recorded() {
        WorkflowEventStore store = new WorkflowEventStore();
        store.record(event(WORKFLOW_STARTED, "instance-1"));

        new WorkflowEventStoreAssert(store).isNotEmpty();
    }

    @Test
    @DisplayName("isNotEmpty_fails_when_no_events_recorded")
    void isNotEmpty_fails_when_no_events_recorded() {
        WorkflowEventStore store = new WorkflowEventStore();

        assertThatThrownBy(() -> new WorkflowEventStoreAssert(store).isNotEmpty())
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("hasSize_passes_when_event_count_matches")
    void hasSize_passes_when_event_count_matches() {
        WorkflowEventStore store = new WorkflowEventStore();
        store.record(event(WORKFLOW_STARTED, "instance-1"));
        store.record(event(WORKFLOW_COMPLETED, "instance-1"));

        new WorkflowEventStoreAssert(store).hasSize(2);
    }

    @Test
    @DisplayName("hasSize_fails_when_event_count_does_not_match")
    void hasSize_fails_when_event_count_does_not_match() {
        WorkflowEventStore store = new WorkflowEventStore();
        store.record(event(WORKFLOW_STARTED, "instance-1"));

        assertThatThrownBy(() -> new WorkflowEventStoreAssert(store).hasSize(2))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("filteredByInstanceId_scopes_assertions_to_the_matching_instance")
    void filteredByInstanceId_scopes_assertions_to_the_matching_instance() {
        WorkflowEventStore store = new WorkflowEventStore();
        store.record(event(WORKFLOW_STARTED, "instance-1"));
        store.record(event(WORKFLOW_COMPLETED, "instance-1"));
        store.record(event(WORKFLOW_STARTED, "instance-2"));

        new WorkflowEventStoreAssert(store)
                .filteredByInstanceId("instance-1")
                .hasEventType(WORKFLOW_COMPLETED);
    }

    @Test
    @DisplayName("filteredByInstanceId_returns_an_empty_list_for_an_unknown_instance")
    void filteredByInstanceId_returns_an_empty_list_for_an_unknown_instance() {
        WorkflowEventStore store = new WorkflowEventStore();
        store.record(event(WORKFLOW_STARTED, "instance-1"));

        new WorkflowEventStoreAssert(store).filteredByInstanceId("unknown-instance").isEmpty();
    }

    @Test
    @DisplayName("filteredByType_scopes_assertions_to_the_matching_type")
    void filteredByType_scopes_assertions_to_the_matching_type() {
        WorkflowEventStore store = new WorkflowEventStore();
        store.record(event(WORKFLOW_STARTED, "instance-1"));
        store.record(event(TASK_COMPLETED, "instance-1", "sendEmail"));

        new WorkflowEventStoreAssert(store)
                .filteredByType(TASK_COMPLETED)
                .hasSize(1)
                .first()
                .hasTaskName("sendEmail");
    }

    @Test
    @DisplayName("filteredByTaskName_scopes_assertions_to_the_matching_task")
    void filteredByTaskName_scopes_assertions_to_the_matching_task() {
        WorkflowEventStore store = new WorkflowEventStore();
        store.record(event(TASK_COMPLETED, "instance-1", "sendEmail"));
        store.record(event(TASK_COMPLETED, "instance-1", "chargeCard"));

        new WorkflowEventStoreAssert(store)
                .filteredByTaskName("chargeCard")
                .hasSize(1);
    }
}