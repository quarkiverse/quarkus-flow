package io.quarkiverse.flow.structuredlogging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.config.TimestampFormat;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

/**
 * Verifies that every structured logging event carries the {@code workflowApplicationId}, so the Data Index can
 * extract it from the event data (see quarkiverse/quarkus-flow#989).
 */
class EventFormatterWorkflowApplicationIdTest {

    private static final String APP_ID = "flow-pool-member-01";
    private static final String WORKFLOW_APPLICATION_ID = "workflowApplicationId";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EventFormatter formatter;

    @BeforeEach
    void setUp() {
        FlowStructuredLoggingConfig config = mock(FlowStructuredLoggingConfig.class);
        when(config.timestampFormat()).thenReturn(TimestampFormat.ISO8601);
        when(config.includeWorkflowPayloads()).thenReturn(false);
        when(config.includeTaskPayloads()).thenReturn(false);
        when(config.includeErrorContext()).thenReturn(true);
        when(config.payloadMaxSize()).thenReturn(10240);
        when(config.truncatePreviewSize()).thenReturn(1024);
        when(config.stackTraceMaxLines()).thenReturn(5);
        formatter = new EventFormatter(config, MAPPER);
    }

    @Test
    @DisplayName("test_workflow_and_task_events_include_workflow_application_id")
    void test_workflow_and_task_events_include_workflow_application_id() {
        FormattingListener listener = new FormattingListener(formatter);
        Workflow workflow = FlowWorkflowBuilder.workflow("structured-logging-app-id")
                .tasks(t -> t.set("setValue", s -> s.expr(Map.of("done", true))))
                .build();

        try (WorkflowApplication app = newApplication(listener)) {
            app.workflowDefinition(workflow).instance(Map.of()).start().join();
        }

        assertThat(listener.eventTypes()).contains(
                "io.serverlessworkflow.workflow.started.v1",
                "io.serverlessworkflow.task.started.v1",
                "io.serverlessworkflow.task.completed.v1",
                "io.serverlessworkflow.workflow.completed.v1");
        assertThat(listener.events)
                .allSatisfy(event -> assertThat(event)
                        .as("structured logging event %s", event.get("eventType"))
                        .containsEntry(WORKFLOW_APPLICATION_ID, APP_ID));
    }

    @Test
    @DisplayName("test_faulted_events_include_workflow_application_id")
    void test_faulted_events_include_workflow_application_id() {
        FormattingListener listener = new FormattingListener(formatter);
        Workflow workflow = FlowWorkflowBuilder.workflow("structured-logging-app-id-faulted")
                .tasks(t -> t.raise("raiseError", r -> r.error(
                        e -> e.type(URI.create("http://example.com/error")).status(500))))
                .build();

        try (WorkflowApplication app = newApplication(listener)) {
            assertThatThrownBy(() -> app.workflowDefinition(workflow).instance(Map.of()).start().join());
        }

        assertThat(listener.eventTypes()).contains(
                "io.serverlessworkflow.task.faulted.v1",
                "io.serverlessworkflow.workflow.faulted.v1");
        assertThat(listener.events)
                .allSatisfy(event -> assertThat(event)
                        .as("structured logging event %s", event.get("eventType"))
                        .containsEntry(WORKFLOW_APPLICATION_ID, APP_ID));
    }

    private static WorkflowApplication newApplication(WorkflowExecutionListener listener) {
        return WorkflowApplication.builder()
                .withId(APP_ID)
                .withListener(listener)
                .disableLifeCycleCEPublishing()
                .build();
    }

    /**
     * Formats real engine events with {@link EventFormatter}, like {@link StructuredLoggingListener} does.
     */
    private static final class FormattingListener implements WorkflowExecutionListener {

        private final EventFormatter formatter;
        private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();

        FormattingListener(EventFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        public void onWorkflowStarted(WorkflowStartedEvent event) {
            add(formatter.formatWorkflowStarted(event));
        }

        @Override
        public void onWorkflowCompleted(WorkflowCompletedEvent event) {
            add(formatter.formatWorkflowCompleted(event));
        }

        @Override
        public void onWorkflowFailed(WorkflowFailedEvent event) {
            add(formatter.formatWorkflowFailed(event));
        }

        @Override
        public void onTaskStarted(TaskStartedEvent event) {
            add(formatter.formatTaskStarted(event));
        }

        @Override
        public void onTaskCompleted(TaskCompletedEvent event) {
            add(formatter.formatTaskCompleted(event));
        }

        @Override
        public void onTaskFailed(TaskFailedEvent event) {
            add(formatter.formatTaskFailed(event));
        }

        List<String> eventTypes() {
            return events.stream().map(e -> {
                Object eventType = e.get("eventType");
                if (eventType instanceof String value) {
                    return value;
                }
                throw new IllegalStateException("Expected eventType to be a String but was: " + eventType);
            }).toList();
        }

        private void add(String json) {
            try {
                events.add(MAPPER.readValue(json, new TypeReference<>() {
                }));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
