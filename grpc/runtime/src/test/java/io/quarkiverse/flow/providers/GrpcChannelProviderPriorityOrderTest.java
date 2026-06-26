package io.quarkiverse.flow.providers;

import static io.quarkiverse.flow.providers.GrpcChannelProvider.DEFAULT_CHANNEL_NAME;
import static io.quarkiverse.flow.providers.GrpcChannelProvider.resolveClientName;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.config.FlowGrpcConfig;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

class GrpcChannelProviderPriorityOrderTest {

    private static final WorkflowDefinitionId WORKFLOW = new WorkflowDefinitionId("org.acme", "grpcGreeting", "0.0.1");

    private static final String TASK_KEY = "org.acme:grpcGreeting:0.0.1:greet";
    private static final String WORKFLOW_KEY = "org.acme:grpcGreeting:0.0.1";
    private static final String VERSIONLESS_KEY = "org.acme:grpcGreeting";

    private static FlowGrpcConfig.ClientOverrideConfig override(String name) {
        return () -> Optional.ofNullable(name);
    }

    /** No Quarkus gRPC clients are registered. */
    private static final Predicate<String> NO_CHANNELS = name -> false;

    /** Treats the given names as registered Quarkus gRPC clients. */
    private static Predicate<String> channels(String... names) {
        return Set.of(names)::contains;
    }

    @Nested
    @DisplayName("config overrides")
    class ConfigOverrides {

        @Test
        @DisplayName("A task-level override takes precedence over workflow, versionless and channel-existence rules")
        void task_override_wins_over_everything() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    TASK_KEY, override("taskClient"),
                    WORKFLOW_KEY, override("workflowClient"),
                    VERSIONLESS_KEY, override("versionlessClient"));

            String resolved = resolveClientName(overrides, channels(WORKFLOW_KEY, DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo("taskClient");
        }

        @Test
        @DisplayName("A version-specific workflow override takes precedence over the versionless override")
        void workflow_override_wins_over_versionless() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    WORKFLOW_KEY, override("workflowClient"),
                    VERSIONLESS_KEY, override("versionlessClient"));

            String resolved = resolveClientName(overrides, channels(WORKFLOW_KEY, DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo("workflowClient");
        }

        @Test
        @DisplayName("The versionless override is applied when no version-specific override exists")
        void versionless_override_applies_when_no_version_specific_override() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    VERSIONLESS_KEY, override("versionlessClient"));

            String resolved = resolveClientName(overrides, channels(WORKFLOW_KEY, DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo("versionlessClient");
        }

        @Test
        @DisplayName("An explicit versionless override wins over the workflow-id-named client and the default channel")
        void versionless_override_wins_over_workflow_id_named_client_and_default() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    VERSIONLESS_KEY, override("versionlessClient"));

            // Both the workflow-id-named client and the default channel exist, but the explicit override wins.
            String resolved = resolveClientName(overrides, channels(WORKFLOW_KEY, DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo("versionlessClient");
        }

        @Test
        @DisplayName("An override whose client name is absent is ignored and resolution continues")
        void override_with_empty_name_is_ignored() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    VERSIONLESS_KEY, override(null));

            String resolved = resolveClientName(overrides, channels(DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo(DEFAULT_CHANNEL_NAME);
        }
    }

    @Nested
    @DisplayName("channel existence fallbacks")
    class ChannelExistenceFallbacks {

        @Test
        @DisplayName("A client named after the workflow id wins over the default channel")
        void workflow_id_named_client_wins_over_default_channel() {
            String resolved = resolveClientName(Map.of(), channels(WORKFLOW_KEY, DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo(WORKFLOW_KEY);
        }

        @Test
        @DisplayName("The default channel is used when no workflow-id-named client exists")
        void default_channel_used_when_no_workflow_id_named_client() {
            String resolved = resolveClientName(Map.of(), channels(DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo(DEFAULT_CHANNEL_NAME);
        }

        @Test
        @DisplayName("Resolution returns null (SDK fallback) when no override or channel matches")
        void sdk_fallback_returns_null_when_nothing_matches() {
            String resolved = resolveClientName(Map.of(), NO_CHANNELS, WORKFLOW, "greet");

            assertThat(resolved).isNull();
        }
    }

    @Test
    @DisplayName("A null task name skips the task-level override lookup and falls through to the workflow override")
    void null_task_name_skips_task_level_override() {
        Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                TASK_KEY, override("taskClient"),
                WORKFLOW_KEY, override("workflowClient"));

        String resolved = resolveClientName(overrides, NO_CHANNELS, WORKFLOW, null);

        assertThat(resolved).isEqualTo("workflowClient");
    }
}
