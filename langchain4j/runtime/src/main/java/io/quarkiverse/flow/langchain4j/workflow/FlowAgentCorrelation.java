package io.quarkiverse.flow.langchain4j.workflow;

import java.util.Map;

import org.slf4j.MDC;

import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;

/**
 * Exposes Quarkus Flow correlation metadata (workflow instance id, task position, task name)
 * to LangChain4j Agentic code via AgenticScope state and MDC.
 */
final class FlowAgentCorrelation {

    static final String VAR_WORKFLOW_INSTANCE_ID = "workflowInstanceID";
    static final String VAR_TASK_POSITION = "taskPosition";
    static final String VAR_TASK_NAME = "taskName";

    // Keep these aligned with TraceLoggerExecutionListener so logging/telemetry sees the same keys.
    private static final String MDC_INSTANCE = "quarkus.flow.instanceId";
    private static final String MDC_TASK_POS = "quarkus.flow.taskPos";
    private static final String MDC_TASK_NAME = "quarkus.flow.task";

    private FlowAgentCorrelation() {
    }

    static void withCorrelation(DefaultAgenticScope scope, WorkflowContextData workflow, TaskContextData task, Runnable r) {
        String workflowInstanceId = (workflow != null && workflow.instanceData() != null) ? workflow.instanceData().id() : null;
        String taskPosition = (task != null && task.position() != null) ? task.position().jsonPointer() : null;
        String taskName = (task != null) ? task.taskName() : null;

        withCorrelation(scope, workflowInstanceId, taskPosition, taskName, r);
    }

    static void withCorrelation(DefaultAgenticScope scope, String workflowInstanceId, String taskPosition, String taskName,
            Runnable r) {
        if (scope != null) {
            if (workflowInstanceId != null) {
                scope.writeState(VAR_WORKFLOW_INSTANCE_ID, workflowInstanceId);
            }
            if (taskPosition != null) {
                scope.writeState(VAR_TASK_POSITION, taskPosition);
            }
            if (taskName != null) {
                scope.writeState(VAR_TASK_NAME, taskName);
            }
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
}
