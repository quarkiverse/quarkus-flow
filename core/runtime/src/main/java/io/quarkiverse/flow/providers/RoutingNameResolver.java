package io.quarkiverse.flow.providers;

import java.util.Optional;

import io.quarkiverse.flow.config.FlowHttpConfig;

public class RoutingNameResolver {

    private final FlowHttpConfig flowHttpConfig;

    public RoutingNameResolver(FlowHttpConfig flowHttpConfig) {
        this.flowHttpConfig = flowHttpConfig;
    }

    public String resolveName(String workflowName, String taskName) {
        final FlowHttpConfig.WorkflowRoutingConfig wfCfg = flowHttpConfig.workflow().get(workflowName);
        if (wfCfg == null) {
            return null;
        }

        if (taskName != null && !taskName.isBlank()) {
            final FlowHttpConfig.TaskRoutingConfig taskCfg = wfCfg.task().get(taskName);
            if (taskCfg != null) {
                Optional<String> taskClient = taskCfg.name();
                if (taskClient.isPresent() && !taskClient.get().isBlank()) {
                    return taskClient.get();
                }
            }
        }

        return wfCfg.name().orElse(null);
    }
}
