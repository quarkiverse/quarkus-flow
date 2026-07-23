package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class InstrumentationContextManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentationContextManager.class);
    private final Map<String, InstrumentationContext> workflowInstanceContext = new ConcurrentHashMap<>();
    private final Map<String, Map<String, InstrumentationContext>> workflowInstanceTaskContext = new ConcurrentHashMap<>();

    public InstrumentationContext getWorkflowInstanceContext(String workflowInstanceId) {
        return workflowInstanceContext.get(workflowInstanceId);
    }

    public void removeWorkflowInstanceContext(String workflowInstanceId) {
        workflowInstanceContext.remove(workflowInstanceId);
        workflowInstanceTaskContext.remove(workflowInstanceId);
    }

    public void putWorkflowInstanceContext(String workflowInstanceId, InstrumentationContext context) {
        workflowInstanceContext.put(workflowInstanceId, context);
        workflowInstanceTaskContext.putIfAbsent(workflowInstanceId, new ConcurrentHashMap<>());
    }

    public static String taskContextId(String taskInstanceId, int iteration, int retryAttempt) {
        return taskInstanceId + "-" + iteration + "-" + retryAttempt;
    }

    public void putTaskInstanceInstanceContext(String workflowInstanceId, String taskInstanceId, int iteration,
            int retryAttempt, InstrumentationContext context) {
        workflowInstanceTaskContext.computeIfAbsent(workflowInstanceId, key -> new ConcurrentHashMap<>())
                .put(taskContextId(taskInstanceId, iteration, retryAttempt), context);
    }

    public void removeTaskInstanceInstanceContext(String workflowInstanceId, String taskInstanceId, int iteration,
            int retryAttempt) {
        Map<String, InstrumentationContext> taskContexts = workflowInstanceTaskContext.get(workflowInstanceId);
        if (taskContexts != null) {
            taskContexts.remove(taskContextId(taskInstanceId, iteration, retryAttempt));
        }
    }

    public InstrumentationContext getTaskInstanceContext(String workflowInstanceId, String taskInstanceId, int iteration,
            int retryAttempt) {
        Map<String, InstrumentationContext> taskContexts = workflowInstanceTaskContext.get(workflowInstanceId);
        return taskContexts != null ? taskContexts.get(taskContextId(taskInstanceId, iteration, retryAttempt)) : null;
    }

    private String findParentContextId(String workflowInstanceId, String jsonPosition) {
        Map<String, InstrumentationContext> currentWorkflowInstanceTaskContext = workflowInstanceTaskContext
                .get(workflowInstanceId);
        InstrumentationContext parentInstrumentationContext = null;
        for (InstrumentationContext instrumentationContext : currentWorkflowInstanceTaskContext.values()) {
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

    public InstrumentationContext findEnclosingParentContext(String workflowInstanceId, String jsonPosition) {
        String parentContextId = findParentContextId(workflowInstanceId, jsonPosition);
        if (parentContextId == null) {
            return workflowInstanceContext.get(workflowInstanceId);
        }
        Map<String, InstrumentationContext> currentWorkflowInstanceTaskContext = workflowInstanceTaskContext
                .get(workflowInstanceId);
        InstrumentationContext parentInstrumentationContext = null;
        for (InstrumentationContext instrumentationContext : currentWorkflowInstanceTaskContext.values()) {
            if (parentContextId.equals(instrumentationContext.getJsonPosition())
                    && (parentInstrumentationContext == null
                            || instrumentationContext.getIteration() > parentInstrumentationContext.getIteration())) {
                parentInstrumentationContext = instrumentationContext;
            }
        }
        return parentInstrumentationContext;
    }

    public void ensureAllTaskSpansAreClosed(String workflowInstanceId) {
        Map<String, InstrumentationContext> tasksContext = workflowInstanceTaskContext.get(workflowInstanceId);
        if (tasksContext == null) {
            LOGGER.warn("No tasks instrumentation context map was found for workflowInstanceId: " + workflowInstanceId);
            return;
        }
        tasksContext.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, InstrumentationContext> entry) -> entry.getValue().getStartTime())
                        .reversed())
                .forEach(entry -> {
                    if (entry.getValue().getStartSpan() != null) {
                        entry.getValue().getStartSpan().end();
                    }
                });
    }
}
