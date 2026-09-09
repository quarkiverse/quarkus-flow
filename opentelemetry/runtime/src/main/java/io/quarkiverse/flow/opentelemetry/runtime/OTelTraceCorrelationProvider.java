package io.quarkiverse.flow.opentelemetry.runtime;

import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowInstrumentationContext.getWorkflowInstrumentationContext;

import java.util.Optional;

import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

/**
 * OpenTelemetry-backed {@link TraceCorrelationProvider}.
 * <p>
 * Quarkus Flow's OpenTelemetry integration never makes its spans "current"
 * ({@code Span.makeCurrent()}); spans are held in the per-instance
 * {@link WorkflowInstrumentationContext}. When each span is created,
 * {@link OTelWorkflowExecutionListener} captures its trace identifiers into that per-instance
 * store, so this provider is a pure read of those captured strings keyed by the lifecycle
 * event - it does not touch live spans and does not depend on the order in which
 * {@code WorkflowExecutionListener}s run.
 */
public class OTelTraceCorrelationProvider implements TraceCorrelationProvider {

    @Override
    public Optional<TraceContext> traceContextFor(WorkflowEvent ev) {
        WorkflowInstrumentationContext workflowContext = getWorkflowInstrumentationContext(
                ev.workflowContext().instanceData());
        if (workflowContext == null) {
            return Optional.empty();
        }

        if (ev instanceof TaskEvent taskEvent) {
            TaskEventInfo info = TaskEventInfo.from(taskEvent);
            TraceContext captured = workflowContext.getTaskTraceContext(
                    info.taskId(), info.taskInstanceIteration(), info.taskInstanceRetryAttempt());
            if (captured != null) {
                return Optional.of(captured);
            }
            // No task span captured yet (e.g. task.started reached the logger before the OTel
            // listener) or the entry was evicted: fall back to the workflow-instance span so the
            // line still correlates to the right trace.
        }
        return Optional.ofNullable(workflowContext.getWorkflowTraceContext());
    }
}