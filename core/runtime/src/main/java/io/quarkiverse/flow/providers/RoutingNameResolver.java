package io.quarkiverse.flow.providers;

import io.quarkiverse.flow.config.FlowHttpConfig;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class RoutingNameResolver {

    private static final String KEY_SEPARATOR = ":";

    private final FlowHttpConfig flowHttpConfig;

    public RoutingNameResolver(FlowHttpConfig flowHttpConfig) {
        this.flowHttpConfig = flowHttpConfig;
    }

    public String resolveName(WorkflowDefinitionId workflowId, String taskName) {
        String workflowKey = workflowId.toString();
        String shortKey = workflowId.namespace() + KEY_SEPARATOR + workflowId.name();
        String workflowName = workflowId.name();

        if (taskName != null && !taskName.isBlank()) {
            String taskKey = workflowKey + KEY_SEPARATOR + taskName;
            FlowHttpConfig.ClientOverride taskOverride = flowHttpConfig.client().get(taskKey);
            if (taskOverride != null && taskOverride.name().isPresent()) {
                return taskOverride.name().get();
            }

            String shortTaskKey = shortKey + KEY_SEPARATOR + taskName;
            FlowHttpConfig.ClientOverride shortTaskOverride = flowHttpConfig.client().get(shortTaskKey);
            if (shortTaskOverride != null && shortTaskOverride.name().isPresent()) {
                return shortTaskOverride.name().get();
            }

            // Legacy task-level: workflow.<workflowName>.task.<taskName>.name
            FlowHttpConfig.WorkflowRoutingConfig workflowConfig = flowHttpConfig.workflow().get(workflowName);
            if (workflowConfig != null && workflowConfig.task() != null) {
                FlowHttpConfig.TaskRoutingConfig taskConfig = workflowConfig.task().get(taskName);
                if (taskConfig != null && taskConfig.name().isPresent()) {
                    return taskConfig.name().get();
                }
            }
        }

        FlowHttpConfig.ClientOverride workflowOverride = flowHttpConfig.client().get(workflowKey);
        if (workflowOverride != null && workflowOverride.name().isPresent()) {
            return workflowOverride.name().get();
        }

        FlowHttpConfig.ClientOverride shortWorkflowOverride = flowHttpConfig.client().get(shortKey);
        if (shortWorkflowOverride != null && shortWorkflowOverride.name().isPresent()) {
            return shortWorkflowOverride.name().get();
        }

        // Legacy workflow-level: workflow.<workflowName>.name
        FlowHttpConfig.WorkflowRoutingConfig workflowConfig = flowHttpConfig.workflow().get(workflowName);
        if (workflowConfig != null && workflowConfig.name().isPresent()) {
            return workflowConfig.name().get();
        }

        if (flowHttpConfig.named().containsKey(workflowKey)) {
            return workflowKey;
        }

        return null;
    }
}