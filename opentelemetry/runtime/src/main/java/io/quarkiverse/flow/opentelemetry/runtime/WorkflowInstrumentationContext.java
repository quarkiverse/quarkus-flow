package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowMutableInstance;

public class WorkflowInstrumentationContext implements AutoCloseable {
    private static final String OTEL_CONTEXT = "OTEL_CONTEXT";

    /**
     * How many just-ended task contexts to keep around after they leave the active map, so a
     * listener that runs right after the OpenTelemetry listener on the same terminal event
     * (e.g. the flow logger) can still resolve the task's span. Bounded so a long {@code for}
     * loop or a wide {@code fork} cannot grow it without limit.
     */
    private static final int RECENTLY_ENDED_MAX = 64;

    private final InstrumentationContext workflowInstanceContext;
    private final Map<String, InstrumentationContext> workflowInstanceTaskContext = new ConcurrentHashMap<>();
    private final Map<String, InstrumentationContext> recentlyEndedTaskContext = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, InstrumentationContext> eldest) {
                    return size() > RECENTLY_ENDED_MAX;
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

    /**
     * Moves the task context out of the active map once its span has ended, retaining it
     * briefly in a bounded map (see {@link #getRecentlyEndedTaskContext}) rather than
     * discarding it outright.
     */
    public void removeTaskInstanceInstanceContext(String taskInstanceId, int iteration, int retryAttempt) {
        String key = taskContextId(taskInstanceId, iteration, retryAttempt);
        InstrumentationContext ended = workflowInstanceTaskContext.remove(key);
        if (ended != null) {
            recentlyEndedTaskContext.put(key, ended);
        }
    }

    public InstrumentationContext getTaskInstanceContext(String taskInstanceId, int iteration,
            int retryAttempt) {
        return workflowInstanceTaskContext.get(taskContextId(taskInstanceId, iteration, retryAttempt));
    }

    /**
     * The context of a task whose span has just ended and which has therefore been removed
     * from the active map, or {@code null} once it has been evicted. Used only for late
     * span correlation on the same terminal event; never for parenting new spans.
     */
    public InstrumentationContext getRecentlyEndedTaskContext(String taskInstanceId, int iteration,
            int retryAttempt) {
        return recentlyEndedTaskContext.get(taskContextId(taskInstanceId, iteration, retryAttempt));
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
        // spans in here are already ended; just drop the references
        recentlyEndedTaskContext.clear();
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
