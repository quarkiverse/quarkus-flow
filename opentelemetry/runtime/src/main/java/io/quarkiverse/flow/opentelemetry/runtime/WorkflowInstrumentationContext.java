package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider.TraceContext;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowMutableInstance;

public class WorkflowInstrumentationContext implements AutoCloseable {
    private static final String OTEL_CONTEXT = "OTEL_CONTEXT";

    /**
     * Upper bound on the correlation-only {@link #taskTraceContext} map. It keeps entries for
     * tasks whose span has already ended (so a lifecycle log line for a terminal task event can
     * still carry the task's own span id regardless of listener order), so a long {@code for}
     * loop or a wide {@code fork} would otherwise grow it without limit. On eviction the trace
     * correlation for that task falls back to the workflow-instance identifiers.
     */
    private static final int TRACE_CONTEXT_MAX = 256;

    private final InstrumentationContext workflowInstanceContext;
    private final Map<String, InstrumentationContext> workflowInstanceTaskContext = new ConcurrentHashMap<>();

    /**
     * Trace/span identifiers captured when each span is created, kept only so lifecycle logging
     * can correlate a log line with the right span. This is deliberately independent of
     * {@link #workflowInstanceTaskContext} (the span-parenting map): entries are never removed
     * on a terminal task event, so it must not be used for parenting new spans.
     */
    private volatile TraceContext workflowTraceContext;
    private final Map<String, TraceContext> taskTraceContext = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, TraceContext> eldest) {
                    return size() > TRACE_CONTEXT_MAX;
                }
            });

    public WorkflowInstrumentationContext(InstrumentationContext workflowInstanceContext) {
        this.workflowInstanceContext = workflowInstanceContext;
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
            taskTraceContext.put(taskContextId(taskInstanceId, iteration, retryAttempt), context);
        }
    }

    /**
     * The trace identifiers captured for a task span, or {@code null} if none were captured or
     * the entry has been evicted. Used only for log correlation, never for span parenting.
     */
    public TraceContext getTaskTraceContext(String taskInstanceId, int iteration, int retryAttempt) {
        return taskTraceContext.get(taskContextId(taskInstanceId, iteration, retryAttempt));
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

    public void ensureAllTaskSpansAreClosed() {
        workflowInstanceTaskContext.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, InstrumentationContext> entry) -> entry.getValue().getStartTime())
                        .reversed())
                .forEach(entry -> {
                    if (entry.getValue().getStartSpan() != null) {
                        entry.getValue().getStartSpan().end();
                    }
                });
        workflowInstanceTaskContext.clear();
        taskTraceContext.clear();
    }

    @Override
    public void close() throws Exception {
        ensureAllTaskSpansAreClosed();
    }

    public static void setWorkflowInstrumentationContext(WorkflowInstanceData instanceData,
            WorkflowInstrumentationContext workflowContext) {
        ((WorkflowMutableInstance) instanceData).addMetadataIfAbsent(OTEL_CONTEXT, () -> workflowContext);
    }

    public static WorkflowInstrumentationContext getWorkflowInstrumentationContext(WorkflowInstanceData instanceData) {
        return instanceData.findMetadata(OTEL_CONTEXT, WorkflowInstrumentationContext.class).orElse(null);
    }
}