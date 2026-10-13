package io.quarkiverse.flow.opentelemetry.runtime;

import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowInstrumentationContext.getWorkflowInstrumentationContext;
import static io.serverlessworkflow.impl.lifecycle.EventType.TASK_CANCELLED;
import static io.serverlessworkflow.impl.lifecycle.EventType.TASK_COMPLETED;
import static io.serverlessworkflow.impl.lifecycle.EventType.TASK_FAULTED;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.quarkiverse.flow.tracing.TraceCorrelationProvider;
import io.serverlessworkflow.impl.lifecycle.EventType;
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

    private final AtomicInteger registeredConsumers = new AtomicInteger();

    @Override
    public void registerConsumer() {
        registeredConsumers.incrementAndGet();
    }

    @Override
    public int registeredConsumerCount() {
        return registeredConsumers.get();
    }

    @Override
    public Optional<TraceContext> traceContextFor(WorkflowEvent ev) {
        WorkflowInstrumentationContext workflowContext = getWorkflowInstrumentationContext(
                ev.workflowContext().instanceData());
        if (workflowContext == null) {
            return Optional.empty();
        }

        if (ev instanceof TaskEvent taskEvent) {
            var taskContext = taskEvent.taskContext();
            String taskId = taskContext.position().jsonPointer();
            int iteration = taskContext.iteration();
            int retryAttempt = taskContext.retryAttempt();

            TraceContext captured = isTerminal(ev.type())
                    ? workflowContext.consumeTerminalTaskTraceContext(taskId, iteration, retryAttempt)
                    : workflowContext.getTaskTraceContext(taskId, iteration, retryAttempt);
            if (captured != null) {
                return Optional.of(captured);
            }
            // No task span captured yet (e.g. task.started reached the logger before the OTel
            // listener) or the entry was evicted: fall back to the workflow-instance span so the
            // line still correlates to the right trace.
        }
        return Optional.ofNullable(workflowContext.getWorkflowTraceContext());
    }

    /**
     * A task has exactly one terminal event, so once every registered consumer has read its
     * trace context during that event's dispatch, the entry can be safely released - see
     * {@link WorkflowInstrumentationContext#consumeTerminalTaskTraceContext}. Suspended/resumed
     * tasks read the same entry multiple times before actually terminating, so only these three
     * event types may trigger that release.
     */
    private static boolean isTerminal(EventType type) {
        return type == TASK_COMPLETED || type == TASK_CANCELLED || type == TASK_FAULTED;
    }
}