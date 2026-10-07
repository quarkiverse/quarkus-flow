package io.quarkiverse.flow.opentelemetry.runtime;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_CANCELLED;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_COMPLETED;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_UNKNOWN;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_EXECUTION_END_REASON_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_EXECUTION_END_REASON_ATTR;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.lifecycle.EventType;

class WorkflowInstrumentationContextTest {

    private static final String WORKFLOW_SPAN = "workflow.execute test-workflow";
    private static final String TASK_SPAN = "task.execute test-task";
    private static final String TASK_ID = "do/0/test-task";
    private static final AttributeKey<String> WORKFLOW_END_REASON = AttributeKey
            .stringKey(FLOW_WF_EXECUTION_END_REASON_ATTR);
    private static final AttributeKey<String> TASK_END_REASON = AttributeKey.stringKey(FLOW_TASK_EXECUTION_END_REASON_ATTR);

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    private final Tracer tracer = tracerProvider.get("test");
    private final WorkflowInstanceData instanceData = mock(WorkflowInstanceData.class);

    private WorkflowInstrumentationContext context;

    @BeforeEach
    void setUp() {
        var workflowSpan = tracer.spanBuilder(WORKFLOW_SPAN).startSpan();
        context = new WorkflowInstrumentationContext(instanceData, instrumentationContext(workflowSpan, null));
        context.putTaskInstanceInstanceContext(TASK_ID, 1, 0,
                instrumentationContext(tracer.spanBuilder(TASK_SPAN).startSpan(), TASK_ID));
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    @DisplayName("close_on_a_cancelled_instance_ends_the_task_and_workflow_spans_as_cancelled")
    void closeOnACancelledInstanceEndsTheTaskAndWorkflowSpansAsCancelled() {
        when(instanceData.status()).thenReturn(WorkflowStatus.CANCELLED);

        context.close();

        assertThat(exporter.getFinishedSpanItems())
                .extracting(SpanData::getName)
                .containsExactly(TASK_SPAN, WORKFLOW_SPAN);
        assertThat(span(TASK_SPAN))
                .hasAttribute(TASK_END_REASON, END_REASON_CANCELLED);
        assertThat(span(WORKFLOW_SPAN))
                .hasStatus(StatusData.ok())
                .hasAttribute(WORKFLOW_END_REASON, END_REASON_CANCELLED)
                .hasEventsSatisfyingExactly(event -> event.hasName(EventType.WORKFLOW_CANCELLED.toString()));
        assertThat(context.getTaskInstanceContext(TASK_ID, 1, 0)).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowStatus.class, names = "CANCELLED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("close_on_a_non_cancelled_instance_only_ends_task_spans")
    void closeOnANonCancelledInstanceOnlyEndsTaskSpans(WorkflowStatus status) {
        when(instanceData.status()).thenReturn(status);

        context.close();

        assertThat(exporter.getFinishedSpanItems())
                .singleElement()
                .satisfies(taskSpan -> assertThat(taskSpan)
                        .hasName(TASK_SPAN)
                        .hasAttribute(TASK_END_REASON, END_REASON_UNKNOWN));
    }

    @Test
    @DisplayName("cancelled_event_after_close_does_not_end_the_workflow_span_again")
    void cancelledEventAfterCloseDoesNotEndTheWorkflowSpanAgain() {
        when(instanceData.status()).thenReturn(WorkflowStatus.CANCELLED);

        context.close();
        endWorkflowSpanLikeTheListener(EventType.WORKFLOW_CANCELLED);

        assertThat(span(WORKFLOW_SPAN))
                .hasEventsSatisfyingExactly(event -> event.hasName(EventType.WORKFLOW_CANCELLED.toString()));
    }

    @Test
    @DisplayName("close_after_cancelled_event_does_not_end_the_workflow_span_again")
    void closeAfterCancelledEventDoesNotEndTheWorkflowSpanAgain() {
        when(instanceData.status()).thenReturn(WorkflowStatus.CANCELLED);

        endWorkflowSpanLikeTheListener(EventType.WORKFLOW_CANCELLED);
        context.close();

        assertThat(span(WORKFLOW_SPAN))
                .hasEventsSatisfyingExactly(event -> event.hasName(EventType.WORKFLOW_CANCELLED.toString()));
    }

    @Test
    @DisplayName("close_after_completed_event_does_not_touch_the_workflow_span")
    void closeAfterCompletedEventDoesNotTouchTheWorkflowSpan() {
        when(instanceData.status()).thenReturn(WorkflowStatus.COMPLETED);

        endWorkflowSpanLikeTheListener(EventType.WORKFLOW_COMPLETED);
        context.close();

        assertThat(span(WORKFLOW_SPAN))
                .hasAttribute(WORKFLOW_END_REASON, END_REASON_COMPLETED)
                .hasEventsSatisfyingExactly(event -> event.hasName(EventType.WORKFLOW_COMPLETED.toString()));
    }

    /**
     * Ends the workflow span the way {@link OTelWorkflowExecutionListener} does on a terminal event.
     */
    private void endWorkflowSpanLikeTheListener(EventType eventType) {
        var endReason = (eventType == EventType.WORKFLOW_CANCELLED) ? END_REASON_CANCELLED : END_REASON_COMPLETED;
        context.getWorkflowInstanceContext()
                .endStartSpan(startSpan -> {
                    startSpan.setStatus(StatusCode.OK);
                    startSpan.setAttribute(FLOW_WF_EXECUTION_END_REASON_ATTR, endReason);
                    context.ensureAllTaskSpansAreClosed(endReason);
                    SpanUtils.appendWorkflowEvent(startSpan, eventType);
                });
    }

    private SpanData span(String name) {
        return exporter.getFinishedSpanItems()
                .stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span %s was not exported".formatted(name)));
    }

    private static InstrumentationContext instrumentationContext(Span span, String jsonPosition) {
        return InstrumentationContext.newBuilder()
                .withJsonPosition(jsonPosition)
                .withStartSpan(span)
                .withStartTime(Instant.now())
                .parentContext(Context.root())
                .build();
    }
}
