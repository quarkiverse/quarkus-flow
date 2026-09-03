package io.quarkiverse.flow.opentelemetry.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.quarkiverse.flow.tracing.TraceCorrelationProvider.TraceContext;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowPosition;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

@DisplayName("OTelTraceCorrelationProvider span resolution")
class OTelTraceCorrelationProviderTest {

    private static final String WF_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String WF_SPAN_ID = "b7ad6b7169203331";
    private static final String TASK_SPAN_ID = "00f067aa0ba902b7";
    private static final String TASK_ID = "do/0/set-0";

    private final OTelTraceCorrelationProvider provider = new OTelTraceCorrelationProvider();

    private static Span span(String traceId, String spanId) {
        return Span.wrap(SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault()));
    }

    private static InstrumentationContext contextWith(Span span) {
        return InstrumentationContext.newBuilder()
                .withStartSpan(span)
                .withStartTime(Instant.now())
                .build();
    }

    private static WorkflowStartedEvent workflowEventWith(WorkflowInstrumentationContext ctx) {
        WorkflowStartedEvent ev = mock(WorkflowStartedEvent.class, RETURNS_DEEP_STUBS);
        WorkflowInstanceData instanceData = ev.workflowContext().instanceData();
        when(instanceData.findMetadata(anyString(), eq(WorkflowInstrumentationContext.class)))
                .thenReturn(Optional.ofNullable(ctx));
        return ev;
    }

    private static TaskStartedEvent taskEventWith(WorkflowInstrumentationContext ctx) {
        TaskStartedEvent ev = mock(TaskStartedEvent.class, RETURNS_DEEP_STUBS);
        WorkflowInstanceData instanceData = ev.workflowContext().instanceData();
        when(instanceData.findMetadata(anyString(), eq(WorkflowInstrumentationContext.class)))
                .thenReturn(Optional.ofNullable(ctx));

        TaskContext taskContext = mock(TaskContext.class);
        WorkflowPosition position = mock(WorkflowPosition.class);
        when(position.jsonPointer()).thenReturn(TASK_ID);
        when(taskContext.position()).thenReturn(position);
        when(taskContext.taskName()).thenReturn("set-0");
        when(taskContext.task()).thenReturn(new SetTask());
        when(taskContext.iteration()).thenReturn(0);
        when(taskContext.isRetrying()).thenReturn(false);
        when(taskContext.retryAttempt()).thenReturn(0);
        when(taskContext.tryRetryCount()).thenReturn(Optional.empty());
        when(ev.taskContext()).thenReturn(taskContext);
        return ev;
    }

    @Test
    @DisplayName("returns the workflow-instance span for a workflow event")
    void workflow_event_resolves_instance_span() {
        WorkflowInstrumentationContext ctx = new WorkflowInstrumentationContext(contextWith(span(WF_TRACE_ID, WF_SPAN_ID)));

        Optional<TraceContext> result = provider.traceContextFor(workflowEventWith(ctx));

        assertThat(result).contains(new TraceContext(WF_TRACE_ID, WF_SPAN_ID, "true", ""));
    }

    @Test
    @DisplayName("returns empty when the instance has no instrumentation context")
    void no_instrumentation_context_returns_empty() {
        assertThat(provider.traceContextFor(workflowEventWith(null))).isEmpty();
    }

    @Test
    @DisplayName("returns empty when the tracked span is invalid")
    void invalid_span_returns_empty() {
        WorkflowInstrumentationContext ctx = new WorkflowInstrumentationContext(contextWith(Span.getInvalid()));

        assertThat(provider.traceContextFor(workflowEventWith(ctx))).isEmpty();
    }

    @Test
    @DisplayName("returns the task span for a task event that has one registered")
    void task_event_resolves_task_span() {
        WorkflowInstrumentationContext ctx = new WorkflowInstrumentationContext(contextWith(span(WF_TRACE_ID, WF_SPAN_ID)));
        ctx.putTaskInstanceInstanceContext(TASK_ID, 0, 0, contextWith(span(WF_TRACE_ID, TASK_SPAN_ID)));

        Optional<TraceContext> result = provider.traceContextFor(taskEventWith(ctx));

        assertThat(result).contains(new TraceContext(WF_TRACE_ID, TASK_SPAN_ID, "true", ""));
    }

    @Test
    @DisplayName("resolves the task span from the just-ended set on a terminal task event")
    void task_event_resolves_recently_ended_task_span() {
        WorkflowInstrumentationContext ctx = new WorkflowInstrumentationContext(contextWith(span(WF_TRACE_ID, WF_SPAN_ID)));
        ctx.putTaskInstanceInstanceContext(TASK_ID, 0, 0, contextWith(span(WF_TRACE_ID, TASK_SPAN_ID)));
        // the OTel listener ends the span and retires the context before the logger runs
        ctx.removeTaskInstanceInstanceContext(TASK_ID, 0, 0);

        Optional<TraceContext> result = provider.traceContextFor(taskEventWith(ctx));

        assertThat(result).contains(new TraceContext(WF_TRACE_ID, TASK_SPAN_ID, "true", ""));
    }

    @Test
    @DisplayName("falls back to the instance span for a task event with no task span yet")
    void task_event_without_task_span_falls_back_to_instance_span() {
        WorkflowInstrumentationContext ctx = new WorkflowInstrumentationContext(contextWith(span(WF_TRACE_ID, WF_SPAN_ID)));

        Optional<TraceContext> result = provider.traceContextFor(taskEventWith(ctx));

        assertThat(result).contains(new TraceContext(WF_TRACE_ID, WF_SPAN_ID, "true", ""));
    }

    @Test
    @DisplayName("reports sampled flag and parent span id from a real SDK span")
    void sdk_span_exposes_sampled_and_parent_id() {
        Tracer tracer = SdkTracerProvider.builder().build().get("test");
        Span parent = tracer.spanBuilder("parent").startSpan();
        Span child = tracer.spanBuilder("child")
                .setParent(Context.root().with(parent))
                .startSpan();

        WorkflowInstrumentationContext ctx = new WorkflowInstrumentationContext(contextWith(child));

        Optional<TraceContext> result = provider.traceContextFor(workflowEventWith(ctx));

        assertThat(result).hasValueSatisfying(tc -> {
            assertThat(tc.traceId()).isEqualTo(child.getSpanContext().getTraceId());
            assertThat(tc.spanId()).isEqualTo(child.getSpanContext().getSpanId());
            assertThat(tc.sampled()).isEqualTo("true");
            assertThat(tc.parentId()).isEqualTo(parent.getSpanContext().getSpanId());
        });

        child.end();
        parent.end();
    }
}