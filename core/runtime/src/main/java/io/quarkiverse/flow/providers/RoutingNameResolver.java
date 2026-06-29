package io.quarkiverse.flow.providers;

import io.quarkiverse.flow.config.FlowHttpConfig;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class RoutingNameResolver {

    private final FlowHttpConfig flowHttpConfig;

    public RoutingNameResolver(FlowHttpConfig flowHttpConfig) {
        this.flowHttpConfig = flowHttpConfig;
    }

    public String resolveName(WorkflowDefinitionId workflowId, String taskName) {
        String workflowIdStr = workflowId.toString();
        String shortId = workflowId.namespace() + ":" + workflowId.name();
        String workflowName = workflowId.name();

        if (taskName != null && !taskName.isBlank()) {
            String taskKey = workflowIdStr + ":" + taskName;
            FlowHttpConfig.ClientOverride taskOverride = flowHttpConfig.client().get(taskKey);
            if (taskOverride != null && taskOverride.name().isPresent()) {
                return taskOverride.name().get();
            }

            String shortTaskKey = shortId + ":" + taskName;
            FlowHttpConfig.ClientOverride shortTaskOverride = flowHttpConfig.client().get(shortTaskKey);
            if (shortTaskOverride != null && shortTaskOverride.name().isPresent()) {
                return shortTaskOverride.name().get();
            }

            // quarkus.flow.http.client.workflow.<workflowName>.task.<taskName>.name
            FlowHttpConfig.WorkflowRoutingConfig workflowConfig = flowHttpConfig.workflow().get(workflowName);
            if (workflowConfig != null && workflowConfig.task() != null) {
                FlowHttpConfig.TaskRoutingConfig taskConfig = workflowConfig.task().get(taskName);
                if (taskConfig != null && taskConfig.name().isPresent()) {
                    return taskConfig.name().get();
                }
            }
        }

        FlowHttpConfig.ClientOverride workflowOverride = flowHttpConfig.client().get(workflowIdStr);
        if (workflowOverride != null && workflowOverride.name().isPresent()) {
            return workflowOverride.name().get();
        }

        FlowHttpConfig.ClientOverride shortWorkflowOverride = flowHttpConfig.client().get(shortId);
        if (shortWorkflowOverride != null && shortWorkflowOverride.name().isPresent()) {
            return shortWorkflowOverride.name().get();
        }

        // quarkus.flow.http.client.workflow.<workflowName>.name
        FlowHttpConfig.WorkflowRoutingConfig workflowConfig = flowHttpConfig.workflow().get(workflowName);
        if (workflowConfig != null && workflowConfig.name().isPresent()) {
            return workflowConfig.name().get();
        }

        if (flowHttpConfig.named().containsKey(workflowIdStr)) {
            return workflowIdStr;
        }

        return null;
    }
}
