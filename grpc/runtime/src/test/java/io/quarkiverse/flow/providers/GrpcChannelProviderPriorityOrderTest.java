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

    private static final String TASK_FULL_KEY = "org.acme:grpcGreeting:0.0.1.task.greet";
    private static final String TASK_MEDIUM_KEY = "org.acme:grpcGreeting.task.greet";
    private static final String TASK_SHORT_KEY = "grpcGreeting.task.greet";
    private static final String WORKFLOW_FULL_KEY = "org.acme:grpcGreeting:0.0.1";
    private static final String WORKFLOW_MEDIUM_KEY = "org.acme:grpcGreeting";
    private static final String WORKFLOW_SHORT_KEY = "grpcGreeting";

    private static FlowGrpcConfig.ClientOverrideConfig override(String name) {
        return () -> Optional.ofNullable(name);
    }

    private static final Predicate<String> NO_CHANNELS = name -> false;

    private static Predicate<String> channels(String... names) {
        return Set.of(names)::contains;
    }

    @Nested
    @DisplayName("config overrides — 6-level cascade")
    class ConfigOverrides {

        @Test
        @DisplayName("task full key wins over all other levels")
        void task_full_wins() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    TASK_FULL_KEY, override("taskFullClient"),
                    TASK_MEDIUM_KEY, override("taskMediumClient"),
                    TASK_SHORT_KEY, override("taskShortClient"),
                    WORKFLOW_FULL_KEY, override("wfFullClient"),
                    WORKFLOW_MEDIUM_KEY, override("wfMediumClient"),
                    WORKFLOW_SHORT_KEY, override("wfShortClient"));

            String resolved = resolveClientName(overrides, channels(WORKFLOW_FULL_KEY, DEFAULT_CHANNEL_NAME), WORKFLOW,
                    "greet");

            assertThat(resolved).isEqualTo("taskFullClient");
        }

        @Test
        @DisplayName("task medium key wins when full is absent")
        void task_medium_wins() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    TASK_MEDIUM_KEY, override("taskMediumClient"),
                    TASK_SHORT_KEY, override("taskShortClient"),
                    WORKFLOW_FULL_KEY, override("wfFullClient"));

            String resolved = resolveClientName(overrides, NO_CHANNELS, WORKFLOW, "greet");

            assertThat(resolved).isEqualTo("taskMediumClient");
        }

        @Test
        @DisplayName("task short key wins when full and medium are absent")
        void task_short_wins() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    TASK_SHORT_KEY, override("taskShortClient"),
                    WORKFLOW_FULL_KEY, override("wfFullClient"));

            String resolved = resolveClientName(overrides, NO_CHANNELS, WORKFLOW, "greet");

            assertThat(resolved).isEqualTo("taskShortClient");
        }

        @Test
        @DisplayName("workflow full key wins when no task overrides")
        void workflow_full_wins() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    WORKFLOW_FULL_KEY, override("wfFullClient"),
                    WORKFLOW_MEDIUM_KEY, override("wfMediumClient"),
                    WORKFLOW_SHORT_KEY, override("wfShortClient"));

            String resolved = resolveClientName(overrides, channels(WORKFLOW_FULL_KEY, DEFAULT_CHANNEL_NAME), WORKFLOW,
                    "greet");

            assertThat(resolved).isEqualTo("wfFullClient");
        }

        @Test
        @DisplayName("workflow medium key wins when full is absent")
        void workflow_medium_wins() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    WORKFLOW_MEDIUM_KEY, override("wfMediumClient"),
                    WORKFLOW_SHORT_KEY, override("wfShortClient"));

            String resolved = resolveClientName(overrides, channels(WORKFLOW_FULL_KEY, DEFAULT_CHANNEL_NAME), WORKFLOW,
                    "greet");

            assertThat(resolved).isEqualTo("wfMediumClient");
        }

        @Test
        @DisplayName("workflow short key is the last override level")
        void workflow_short_wins() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    WORKFLOW_SHORT_KEY, override("wfShortClient"));

            String resolved = resolveClientName(overrides, channels(DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo("wfShortClient");
        }

        @Test
        @DisplayName("an override whose client name is absent is ignored and resolution continues")
        void override_with_empty_name_is_ignored() {
            Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                    WORKFLOW_SHORT_KEY, override(null));

            String resolved = resolveClientName(overrides, channels(DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo(DEFAULT_CHANNEL_NAME);
        }
    }

    @Nested
    @DisplayName("channel existence fallbacks")
    class ChannelExistenceFallbacks {

        @Test
        @DisplayName("the default channel is used when no override matches")
        void default_channel_used_when_no_overrides() {
            String resolved = resolveClientName(Map.of(), channels(DEFAULT_CHANNEL_NAME), WORKFLOW, "greet");

            assertThat(resolved).isEqualTo(DEFAULT_CHANNEL_NAME);
        }

        @Test
        @DisplayName("resolution returns null (SDK fallback) when no override or channel matches")
        void sdk_fallback_returns_null_when_nothing_matches() {
            String resolved = resolveClientName(Map.of(), NO_CHANNELS, WORKFLOW, "greet");

            assertThat(resolved).isNull();
        }
    }

    @Test
    @DisplayName("a null task name skips the task-level override lookup and falls through to the workflow override")
    void null_task_name_skips_task_level_override() {
        Map<String, FlowGrpcConfig.ClientOverrideConfig> overrides = Map.of(
                TASK_FULL_KEY, override("taskClient"),
                WORKFLOW_FULL_KEY, override("workflowClient"));

        String resolved = resolveClientName(overrides, NO_CHANNELS, WORKFLOW, null);

        assertThat(resolved).isEqualTo("workflowClient");
    }
}
