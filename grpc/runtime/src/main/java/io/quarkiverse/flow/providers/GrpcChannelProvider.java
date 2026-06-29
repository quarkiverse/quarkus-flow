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
        String workflowIdStr = workflowId.toString();
        String shortId = workflowId.namespace() + ":" + workflowId.name();
        String workflowName = workflowId.name();

        // quarkus.flow.grpc.client.workflow.<workflowName>.task.<taskName>.name
        if (taskName != null && !taskName.isBlank()) {
            String workflowTaskKey = "workflow." + workflowName + ".task." + taskName;
            FlowGrpcConfig.ClientOverrideConfig workflowTaskOverride = overrides.get(workflowTaskKey);
            if (workflowTaskOverride != null && workflowTaskOverride.name().isPresent()) {
                return workflowTaskOverride.name().get();
            }

            // quarkus.flow.grpc.client.<namespace>:<name>:<version>:<task>.name
            String taskKey = workflowIdStr + ":" + taskName;
            FlowGrpcConfig.ClientOverrideConfig taskOverride = overrides.get(taskKey);
            if (taskOverride != null && taskOverride.name().isPresent()) {
                return taskOverride.name().get();
            }

            // quarkus.flow.grpc.client.<namespace>:<name>:<task>.name
            String shortTaskKey = shortId + ":" + taskName;
            FlowGrpcConfig.ClientOverrideConfig shortTaskOverride = overrides.get(shortTaskKey);
            if (shortTaskOverride != null && shortTaskOverride.name().isPresent()) {
                return shortTaskOverride.name().get();
            }
        }

        // quarkus.flow.grpc.client.workflow.<workflowName>.name
        String workflowNameKey = "workflow." + workflowName;
        FlowGrpcConfig.ClientOverrideConfig workflowNameOverride = overrides.get(workflowNameKey);
        if (workflowNameOverride != null && workflowNameOverride.name().isPresent()) {
            return workflowNameOverride.name().get();
        }

        // quarkus.flow.grpc.client.<namespace>:<name>:<version>.name
        FlowGrpcConfig.ClientOverrideConfig workflowOverride = overrides.get(workflowIdStr);
        if (workflowOverride != null && workflowOverride.name().isPresent()) {
            return workflowOverride.name().get();
        }

        // quarkus.flow.grpc.client.<namespace>:<name>.name
        FlowGrpcConfig.ClientOverrideConfig shortWorkflowOverride = overrides.get(shortId);
        if (shortWorkflowOverride != null && shortWorkflowOverride.name().isPresent()) {
            return shortWorkflowOverride.name().get();
        }

        // Workflow ID as gRPC client name
        if (channelExists.test(workflowIdStr)) {
            return workflowIdStr;
        }

        // Default channel
        if (channelExists.test(DEFAULT_CHANNEL_NAME)) {
            return DEFAULT_CHANNEL_NAME;
        }

        LOG.debug("No Quarkus gRPC client configured for workflow '{}'; SDK will use its default channel", workflowIdStr);
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
            LOG.warn("Could not resolve named gRPC channel '{}'. Available instances are {}", name, instance.listActive());
            return null;
        }
    }
}
