package io.quarkiverse.flow.opentelemetry.runtime;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_UNKNOWN;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_EXECUTION_END_REASON_ATTR;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowMutableInstance;
import io.serverlessworkflow.impl.persistence.metadata.MetaTransient;

@MetaTransient
public class WorkflowInstrumentationContext implements AutoCloseable {
    private static final String OTEL_CONTEXT = "OTEL_CONTEXT";
    private final InstrumentationContext workflowInstanceContext;
    private final Map<String, InstrumentationContext> workflowInstanceTaskContext = new ConcurrentHashMap<>();

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
    }

    @Override
    public void close() {
        ensureAllTaskSpansAreClosed(END_REASON_UNKNOWN);
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
