package io.quarkiverse.flow.structuredlogging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.config.TimestampFormat;
import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider;
import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider.TraceContext;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

@DisplayName("EventFormatter trace correlation fields")
class EventFormatterTraceCorrelationTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";
    private static final String PARENT_ID = "1100dd0d1d001111";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FlowStructuredLoggingConfig config;

    @BeforeEach
    void setUp() {
        config = mock(FlowStructuredLoggingConfig.class);
        when(config.timestampFormat()).thenReturn(TimestampFormat.ISO8601);
        when(config.enabled()).thenReturn(true);
        when(config.includeWorkflowPayloads()).thenReturn(false);
        when(config.includeTaskPayloads()).thenReturn(false);
    }

    private static WorkflowStartedEvent workflowStartedEvent() {
        WorkflowStartedEvent ev = mock(WorkflowStartedEvent.class, RETURNS_DEEP_STUBS);
        when(ev.eventDate()).thenReturn(OffsetDateTime.now());
        when(ev.workflowContext().instanceData().id()).thenReturn("instance-1");
        return ev;
    }

    private static TaskStartedEvent taskStartedEvent() {
        TaskStartedEvent ev = mock(TaskStartedEvent.class, RETURNS_DEEP_STUBS);
        when(ev.eventDate()).thenReturn(OffsetDateTime.now());
        when(ev.workflowContext().instanceData().id()).thenReturn("instance-1");
        when(ev.taskContext().position().jsonPointer()).thenReturn("do/0/greet");
        when(ev.taskContext().taskName()).thenReturn("greet");
        return ev;
    }

    private JsonNode parse(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("adds traceId/spanId/sampled/parentId to a workflow event when a span is active")
    void workflow_event_carries_trace_fields() throws Exception {
        TraceCorrelationProvider provider = ev -> Optional.of(new TraceContext(TRACE_ID, SPAN_ID, "true", PARENT_ID));
        EventFormatter formatter = new EventFormatter(config, objectMapper, provider);

        JsonNode json = parse(formatter.formatWorkflowStarted(workflowStartedEvent()));

        assertThat(json.get("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(json.get("spanId").asText()).isEqualTo(SPAN_ID);
        assertThat(json.get("sampled").asText()).isEqualTo("true");
        assertThat(json.get("parentId").asText()).isEqualTo(PARENT_ID);
    }

    @Test
    @DisplayName("adds trace fields to a task event too")
    void task_event_carries_trace_fields() throws Exception {
        TraceCorrelationProvider provider = ev -> Optional.of(new TraceContext(TRACE_ID, SPAN_ID, "true", PARENT_ID));
        EventFormatter formatter = new EventFormatter(config, objectMapper, provider);

        JsonNode json = parse(formatter.formatTaskStarted(taskStartedEvent()));

        assertThat(json.get("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(json.get("spanId").asText()).isEqualTo(SPAN_ID);
    }

    @Test
    @DisplayName("omits trace fields when the NOOP provider is used")
    void noop_provider_omits_trace_fields() throws Exception {
        EventFormatter formatter = new EventFormatter(config, objectMapper);

        JsonNode json = parse(formatter.formatWorkflowStarted(workflowStartedEvent()));

        assertThat(json.has("traceId")).isFalse();
        assertThat(json.has("spanId")).isFalse();
        assertThat(json.has("sampled")).isFalse();
        assertThat(json.has("parentId")).isFalse();
    }

    @Test
    @DisplayName("omits trace fields when the provider reports no active span")
    void empty_provider_result_omits_trace_fields() throws Exception {
        EventFormatter formatter = new EventFormatter(config, objectMapper, ev -> Optional.empty());

        JsonNode json = parse(formatter.formatWorkflowStarted(workflowStartedEvent()));

        assertThat(json.has("traceId")).isFalse();
    }
}