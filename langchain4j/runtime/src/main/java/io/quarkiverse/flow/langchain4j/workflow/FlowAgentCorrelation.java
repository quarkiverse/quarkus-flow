package io.quarkiverse.flow.langchain4j.workflow;

import java.util.Map;

import org.slf4j.MDC;

import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;

/**
 * Exposes Quarkus Flow correlation metadata (workflow instance id, task position, task name)
 * to LangChain4j Agentic execution via MDC only.
 */
final class FlowAgentCorrelation {

    // Keep these aligned with TraceLoggerExecutionListener so logging/telemetry sees the same keys.
    static final String MDC_INSTANCE = "quarkus.flow.instanceId";
    static final String MDC_TASK_POS = "quarkus.flow.taskPos";
    static final String MDC_TASK_NAME = "quarkus.flow.task";
    // Internal scope keys used to propagate correlation across threads.
    static final String SCOPE_INSTANCE = "__flow_mdc_instance_id__";
    static final String SCOPE_TASK_POS = "__flow_mdc_task_pos__";
    static final String SCOPE_TASK_NAME = "__flow_mdc_task_name__";

    private FlowAgentCorrelation() {
    }

    static String verificationNull(TaskContextData task) {
        if (task == null) {
            return null;
        }

        String taskPosition = (task.position() != null) ? task.position().jsonPointer() : null;
        if (taskPosition == null || taskPosition.isBlank()) {
            return "/";
        }
        if (!taskPosition.startsWith("/")) {
            return "/" + taskPosition;
        }
        return taskPosition;
    }

    static void withCorrelation(DefaultAgenticScope scope, WorkflowContextData workflow, TaskContextData task, Runnable r) {
        String workflowInstanceId = (workflow != null && workflow.instanceData() != null) ? workflow.instanceData().id() : null;
        String taskPosition = verificationNull(task);
        String taskName = (task != null) ? task.taskName() : null;

        withCorrelation(scope, workflowInstanceId, taskPosition, taskName, r);
    }

    static void withCorrelation(DefaultAgenticScope scope, String workflowInstanceId, String taskPosition, String taskName,
            Runnable r) {
        boolean hasCorrelation = workflowInstanceId != null || taskPosition != null || taskName != null;
        if (!hasCorrelation) {
            r.run();
            return;
        }

        if (scope != null) {
            if (workflowInstanceId != null && !workflowInstanceId.isBlank()) {
                String existingInstanceId = scope.readState("__flow_instance_id__", "");
                if (existingInstanceId == null || existingInstanceId.isBlank()) {
                    scope.writeState("__flow_instance_id__", workflowInstanceId);
                }
            }
            ensureListener(scope);
            // Always write all keys to avoid stale values in subsequent invocations.
            scope.writeState(SCOPE_INSTANCE, workflowInstanceId);
            scope.writeState(SCOPE_TASK_POS, taskPosition);
            scope.writeState(SCOPE_TASK_NAME, taskName);
        }

        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        try {
            if (workflowInstanceId != null) {
                MDC.put(MDC_INSTANCE, workflowInstanceId);
            }
            if (taskPosition != null) {
                MDC.put(MDC_TASK_POS, taskPosition);
            }
            if (taskName != null) {
                MDC.put(MDC_TASK_NAME, taskName);
            }
            r.run();
        } finally {
            if (snapshot == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(snapshot);
            }
        }
    }

    private static void ensureListener(DefaultAgenticScope scope) {
        if (scope == null) {
            return;
        }
        var existing = scope.listener();
        if (existing instanceof FlowAgentCorrelationListener) {
            return;
        }
        scope.setListener(new FlowAgentCorrelationListener(existing));
    }
}
