package io.quarkiverse.flow.providers;

import java.util.Map;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Channel;
import io.quarkiverse.flow.config.FlowGrpcConfig;
import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.Unremovable;
import io.quarkus.grpc.GrpcClient;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.smallrye.config.SmallRyeConfig;

@ApplicationScoped
@Unremovable
public class GrpcChannelProvider implements WorkflowApplicationBuilderCustomizer {

    static final String GRPC_CHANNEL_PROVIDER_KEY = "grpcChannelProvider";

    static final String DEFAULT_CHANNEL_NAME = "flowGrpc";

    private static final String KEY_SEPARATOR = ":";

    private static final Logger LOG = LoggerFactory.getLogger(GrpcChannelProvider.class);

    private final FlowGrpcConfig config = ConfigProvider.getConfig()
            .unwrap(SmallRyeConfig.class)
            .getConfigMapping(FlowGrpcConfig.class);

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        LOG.info("Flow: Registering gRPC channel provider (Quarkus named client routing)");
        builder.withAdditionalObject(GRPC_CHANNEL_PROVIDER_KEY, this::channelFor);
    }

    private Channel channelFor(WorkflowContextData workflowContextData, TaskContextData taskContextData) {
        WorkflowDefinitionId id = workflowContextData.definition().id();
        String taskName = taskContextData.taskName();
        return getNamedChannel(resolveClientName(id, taskName));
    }

    private String resolveClientName(WorkflowDefinitionId workflowId, String taskName) {
        return resolveClientName(config.client(), this::channelExists, workflowId, taskName);
    }

    static String resolveClientName(Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides,
            Predicate<String> channelExists, WorkflowDefinitionId workflowId, String taskName) {
        String workflowKey = workflowId.toString();
        String shortKey = workflowId.namespace() + KEY_SEPARATOR + workflowId.name();
        String workflowName = workflowId.name();

        // 1. Task-level override: namespace:name:version:taskName
        if (taskName != null && !taskName.isBlank()) {
            String taskKey = workflowKey + KEY_SEPARATOR + taskName;
            FlowGrpcConfig.ClientOverrideConfig taskOverride = overrides.get(taskKey);
            if (taskOverride != null && taskOverride.name().isPresent()) {
                return taskOverride.name().get();
            }

            String shortTaskKey = shortKey + KEY_SEPARATOR + taskName;
            FlowGrpcConfig.ClientOverrideConfig shortTaskOverride = overrides.get(shortTaskKey);
            if (shortTaskOverride != null && shortTaskOverride.name().isPresent()) {
                return shortTaskOverride.name().get();
            }

            String legacyTaskKey = "workflow." + workflowName + ".task." + taskName;
            FlowGrpcConfig.ClientOverrideConfig legacyTaskOverride = overrides.get(legacyTaskKey);
            if (legacyTaskOverride != null && legacyTaskOverride.name().isPresent()) {
                return legacyTaskOverride.name().get();
            }
        }

        // 2. Workflow-level override: namespace:name:version
        FlowGrpcConfig.ClientOverrideConfig workflowOverride = overrides.get(workflowKey);
        if (workflowOverride != null && workflowOverride.name().isPresent()) {
            return workflowOverride.name().get();
        }

        // 3. Short form: namespace:name
        FlowGrpcConfig.ClientOverrideConfig shortWorkflowOverride = overrides.get(shortKey);
        if (shortWorkflowOverride != null && shortWorkflowOverride.name().isPresent()) {
            return shortWorkflowOverride.name().get();
        }

        // 4. Legacy: workflow."name"
        String legacyWorkflowKey = "workflow." + workflowName;
        FlowGrpcConfig.ClientOverrideConfig legacyWorkflowOverride = overrides.get(legacyWorkflowKey);
        if (legacyWorkflowOverride != null && legacyWorkflowOverride.name().isPresent()) {
            return legacyWorkflowOverride.name().get();
        }

        // 5. Workflow ID as gRPC client name
        if (channelExists.test(workflowKey)) {
            return workflowKey;
        }

        // 6. Default channel
        if (channelExists.test(DEFAULT_CHANNEL_NAME)) {
            return DEFAULT_CHANNEL_NAME;
        }

        LOG.debug("No Quarkus gRPC client configured for workflow '{}'; SDK will use its default channel",
                workflowKey);
        return null;
    }

    private boolean channelExists(String name) {
        return Arc.container().select(Channel.class, GrpcClient.Literal.of(name)).isResolvable();
    }

    private Channel getNamedChannel(String name) {
        if (name == null) {
            return null;
        }
        InjectableInstance<? extends Channel> instance = Arc.container()
                .select(Channel.class, GrpcClient.Literal.of(name));
        if (instance.isResolvable()) {
            LOG.debug("Resolved named gRPC channel '{}' from Quarkus CDI", name);
            return instance.get();
        } else {
            LOG.warn("Could not resolve named gRPC channel '{}'. Available instances are {}", name,
                    instance.listActive());
            return null;
        }
    }
}
