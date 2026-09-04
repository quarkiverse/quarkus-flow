package io.quarkiverse.flow.opentelemetry.runtime;

import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowInstrumentationContext.getWorkflowInstrumentationContext;

import java.util.Optional;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.quarkiverse.flow.tracing.TraceCorrelationProvider;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

/**
 * OpenTelemetry-backed {@link TraceCorrelationProvider}.
 * <p>
 * Quarkus Flow's OpenTelemetry integration never makes its spans "current"
 * ({@code Span.makeCurrent()}); spans are held in the per-instance
 * {@link WorkflowInstrumentationContext} and their context is propagated explicitly. So this
 * provider resolves the relevant span from that per-instance store keyed by the lifecycle
 * event, rather than reading {@code Span.current()} (which is not valid on the workflow
 * executor thread).
 */
public class OTelTraceCorrelationProvider implements TraceCorrelationProvider {

    @Override
    public Optional<TraceContext> traceContextFor(WorkflowEvent ev) {
        WorkflowInstrumentationContext workflowContext = getWorkflowInstrumentationContext(
                ev.workflowContext().instanceData());
        if (workflowContext == null) {
            return Optional.empty();
        }

        InstrumentationContext context = resolveContext(workflowContext, ev);
        if (context == null || context.getStartSpan() == null) {
            return Optional.empty();
        }

        Span span = context.getStartSpan();
        SpanContext spanContext = span.getSpanContext();
        if (!spanContext.isValid()) {
            return Optional.empty();
        }
        return Optional.of(new TraceContext(
                spanContext.getTraceId(),
                spanContext.getSpanId(),
                Boolean.toString(spanContext.isSampled()),
                parentSpanId(span)));
    }

    /**
     * The parent span id, when the span is an SDK span with a valid parent; otherwise an empty
     * string (kept non-null so the value can be written straight to the MDC).
     */
    private static String parentSpanId(Span span) {
        if (span instanceof ReadableSpan readableSpan) {
            SpanContext parent = readableSpan.getParentSpanContext();
            if (parent.isValid()) {
                return parent.getSpanId();
            }
        }
        return "";
    }

    private static InstrumentationContext resolveContext(WorkflowInstrumentationContext workflowContext, WorkflowEvent ev) {
        if (ev instanceof TaskEvent taskEvent) {
            TaskEventInfo info = TaskEventInfo.from(taskEvent);
            InstrumentationContext taskContext = workflowContext.getTaskInstanceContext(
                    info.taskId(), info.taskInstanceIteration(), info.taskInstanceRetryAttempt());
            if (taskContext == null) {
                // On a terminal task event the OpenTelemetry listener (which runs first) has
                // already ended the span and moved it out of the active map; read it from the
                // just-ended set so the log line still carries the task's own span id.
                taskContext = workflowContext.getRecentlyEndedTaskContext(
                        info.taskId(), info.taskInstanceIteration(), info.taskInstanceRetryAttempt());
            }
            // Still nothing (e.g. task.started, before the span is created): fall back to the
            // workflow-instance span so the line at least correlates to the right trace.
            return taskContext != null ? taskContext : workflowContext.getWorkflowInstanceContext();
        }
        return workflowContext.getWorkflowInstanceContext();
    }
}