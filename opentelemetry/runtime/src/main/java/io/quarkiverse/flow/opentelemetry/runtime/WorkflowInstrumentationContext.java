package io.quarkiverse.flow.opentelemetry.runtime;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_CANCELLED;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_UNKNOWN;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_EXECUTION_END_REASON_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_EXECUTION_END_REASON_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanUtils.appendWorkflowEvent;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.quarkiverse.flow.tracing.TraceCorrelationProvider.TraceContext;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowMutableInstance;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.lifecycle.EventType;
import io.serverlessworkflow.impl.persistence.metadata.MetaTransient;

@MetaTransient
public class WorkflowInstrumentationContext implements AutoCloseable {
    private static final String OTEL_CONTEXT = "OTEL_CONTEXT";
    private static final int TRACE_CONTEXT_MAX = 256;

    private final WorkflowInstanceData instanceData;
    private final InstrumentationContext workflowInstanceContext;
    private final Map<String, InstrumentationContext> workflowInstanceTaskContext = new ConcurrentHashMap<>();

    private volatile TraceContext workflowTraceContext;

    /**
     * Number of registered {@link io.quarkiverse.flow.tracing.TraceCorrelationProvider}
     * consumers expected to read a task's terminal trace context (see
     * {@link #consumeTerminalTaskTraceContext}) before it can be safely released.
     */
    private final int expectedConsumers;

    private final Map<String, TrackedTraceContext> taskTraceContext = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, TrackedTraceContext> eldest) {
                    return size() > TRACE_CONTEXT_MAX;
                }
            });

    public WorkflowInstrumentationContext(WorkflowInstanceData instanceData, InstrumentationContext workflowInstanceContext,
            int expectedConsumers) {
        this.instanceData = instanceData;
        this.workflowInstanceContext = workflowInstanceContext;
        this.expectedConsumers = expectedConsumers;
    }

    /**
     * A captured {@link TraceContext} paired with a countdown of how many registered consumers
     * still need to read it on the task's terminal lifecycle event. A task has exactly one
     * terminal event, so once every consumer has read it during that event's dispatch, the
     * entry can be removed instead of waiting for LRU eviction or workflow completion.
     */
    private static final class TrackedTraceContext {
        private final TraceContext context;
        private final AtomicInteger remainingTerminalReads;

        TrackedTraceContext(TraceContext context, int expectedConsumers) {
            this.context = context;
            this.remainingTerminalReads = new AtomicInteger(expectedConsumers);
        }
    }

    public InstrumentationContext getWorkflowInstanceContext() {
        return workflowInstanceContext;
    }

    public static String taskContextId(String taskInstanceId, int iteration, int retryAttempt) {
        return taskInstanceId + "-" + iteration + "-" + retryAttempt;
    }

    public void putTaskInstanceInstanceContext(String taskInstanceId, int iteration,
            int retryAttempt, InstrumentationContext context) {
        workflowInstanceTaskContext.put(taskContextId(taskInstanceId, iteration, retryAttempt), context);
    }

    public void removeTaskInstanceInstanceContext(String taskInstanceId, int iteration, int retryAttempt) {
        workflowInstanceTaskContext.remove(taskContextId(taskInstanceId, iteration, retryAttempt));
    }

    public InstrumentationContext getTaskInstanceContext(String taskInstanceId, int iteration,
            int retryAttempt) {
        return workflowInstanceTaskContext.get(taskContextId(taskInstanceId, iteration, retryAttempt));
    }

    public TraceContext getWorkflowTraceContext() {
        return workflowTraceContext;
    }

    public void setWorkflowTraceContext(TraceContext workflowTraceContext) {
        this.workflowTraceContext = workflowTraceContext;
    }

    /**
     * Records the trace identifiers of a task span for later log correlation. Safe to call with
     * a {@code null} context (nothing is stored) so callers need not null-check the extraction.
     */
    public void putTaskTraceContext(String taskInstanceId, int iteration, int retryAttempt, TraceContext context) {
        if (context != null) {
            taskTraceContext.put(taskContextId(taskInstanceId, iteration, retryAttempt),
                    new TrackedTraceContext(context, expectedConsumers));
        }
    }

    /**
     * The trace identifiers captured for a task span, or {@code null} if none were captured or
     * the entry has been evicted. Used only for log correlation, never for span parenting.
     * <p>
     * Does not affect the terminal-read countdown: non-terminal events (started, suspended,
     * resumed, retried) can read the same entry an arbitrary number of times before the task
     * finally terminates.
     */
    public TraceContext getTaskTraceContext(String taskInstanceId, int iteration, int retryAttempt) {
        TrackedTraceContext tracked = taskTraceContext.get(taskContextId(taskInstanceId, iteration, retryAttempt));
        return tracked == null ? null : tracked.context;
    }

    /**
     * The trace identifiers captured for a task span, read on its terminal lifecycle event
     * (completed, cancelled or faulted). Removes the entry once every registered consumer has
     * read it for that event, so memory is released as soon as the task ends instead of waiting
     * for LRU eviction or workflow completion.
     */
    public TraceContext consumeTerminalTaskTraceContext(String taskInstanceId, int iteration, int retryAttempt) {
        String key = taskContextId(taskInstanceId, iteration, retryAttempt);
        TrackedTraceContext tracked = taskTraceContext.get(key);
        if (tracked == null) {
            return null;
        }
        if (tracked.remainingTerminalReads.decrementAndGet() <= 0) {
            taskTraceContext.remove(key);
        }
        return tracked.context;
    }

    private String findParentContextId(String jsonPosition) {
        InstrumentationContext parentInstrumentationContext = null;
        for (InstrumentationContext instrumentationContext : workflowInstanceTaskContext.values()) {
            if (instrumentationContext.isContainerContext()
                    && jsonPosition.startsWith(instrumentationContext.getContainerPosition())
                    && !jsonPosition.equals(instrumentationContext.getJsonPosition()) && (parentInstrumentationContext == null
                            || instrumentationContext.getContainerPosition().length() > parentInstrumentationContext
                                    .getContainerPosition().length())) {
                parentInstrumentationContext = instrumentationContext;
            }
        }
        if (parentInstrumentationContext != null) {
            return parentInstrumentationContext.getJsonPosition();
        }
        return null;
    }

    public InstrumentationContext findEnclosingParentContext(String jsonPosition) {
        String parentContextId = findParentContextId(jsonPosition);
        if (parentContextId == null) {
            return workflowInstanceContext;
        }
        InstrumentationContext parentInstrumentationContext = null;
        for (InstrumentationContext instrumentationContext : workflowInstanceTaskContext.values()) {
            if (parentContextId.equals(instrumentationContext.getJsonPosition())
                    && (parentInstrumentationContext == null
                            || instrumentationContext.getIteration() > parentInstrumentationContext.getIteration())) {
                parentInstrumentationContext = instrumentationContext;
            }
        }
        return parentInstrumentationContext;
    }

    public void ensureAllTaskSpansAreClosed(String endReason) {
        endTaskSpans(taskSpan -> taskSpan.setAttribute(FLOW_TASK_EXECUTION_END_REASON_ATTR, endReason));
        workflowInstanceTaskContext.clear();
    }

    public void failActiveTaskSpans(String statusDescription, String errorType, String endReason) {
        endTaskSpans(taskSpan -> {
            taskSpan.setStatus(StatusCode.ERROR, statusDescription);
            taskSpan.setAttribute(ERROR_TYPE, errorType);
            taskSpan.setAttribute(FLOW_TASK_EXECUTION_END_REASON_ATTR, endReason);
        });
        workflowInstanceTaskContext.clear();
    }

    private void endTaskSpans(Consumer<Span> withSettings) {
        workflowInstanceTaskContext.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, InstrumentationContext> entry) -> entry.getValue().getStartTime())
                        .reversed())
                .forEach(entry -> entry.getValue().endStartSpan(withSettings));
        taskTraceContext.clear();
    }

    /**
     * Called by the engine when it clears the instance metadata. For a cancelled instance that can happen before the
     * {@code onWorkflowCancelled} listener event is published (e.g. when cancelling an instance waiting on a
     * {@code listen} task), and then the listener can no longer find this context. The instance status is already
     * {@link WorkflowStatus#CANCELLED} by then, so the workflow span is ended here instead of being lost. The workflow
     * span is only ever ended once, so whichever of this or the cancelled listener event comes first wins.
     */
    @Override
    public void close() {
        if (instanceData.status() == WorkflowStatus.CANCELLED) {
            workflowInstanceContext.endStartSpan(startSpan -> {
                startSpan.setStatus(StatusCode.OK);
                startSpan.setAttribute(FLOW_WF_EXECUTION_END_REASON_ATTR, END_REASON_CANCELLED);
                ensureAllTaskSpansAreClosed(END_REASON_CANCELLED);
                appendWorkflowEvent(startSpan, EventType.WORKFLOW_CANCELLED);
            });
        } else {
            ensureAllTaskSpansAreClosed(END_REASON_UNKNOWN);
        }
    }

    public static void setWorkflowInstrumentationContext(WorkflowInstanceData instanceData,
            WorkflowInstrumentationContext workflowContext) {
        ((WorkflowMutableInstance) instanceData).addMetadataIfAbsent(WorkflowInstrumentationContext.OTEL_CONTEXT,
                () -> workflowContext);
    }

    public static WorkflowInstrumentationContext getWorkflowInstrumentationContext(WorkflowInstanceData instanceData) {
        return instanceData.findMetadata(OTEL_CONTEXT, WorkflowInstrumentationContext.class).orElse(null);
    }
}