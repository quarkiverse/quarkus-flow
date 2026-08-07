package io.quarkiverse.flow.providers;

import java.util.Map;

import io.quarkiverse.flow.config.ClientConfigCascade;
import io.quarkiverse.flow.config.FlowHttpConfig;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class RoutingNameResolver {

    private final FlowHttpConfig flowHttpConfig;

    public RoutingNameResolver(FlowHttpConfig flowHttpConfig) {
        this.flowHttpConfig = flowHttpConfig;
    }

    public String resolveName(WorkflowDefinitionId workflowId, String taskName) {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = flowHttpConfig.workflow();
        return ClientConfigCascade.resolve(key -> overrideName(overrides, key), workflowId, taskName);
    }

    private static String overrideName(Map<String, FlowHttpConfig.ClientOverrideConfig> overrides, String key) {
        FlowHttpConfig.ClientOverrideConfig override = overrides.get(key);
        if (override != null && override.name().isPresent()) {
            return override.name().get();
        }
        return null;
    }
}
