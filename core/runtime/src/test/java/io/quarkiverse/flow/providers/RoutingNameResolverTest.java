package io.quarkiverse.flow.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.config.FlowHttpConfig;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

class RoutingNameResolverTest {

    private static final WorkflowDefinitionId WORKFLOW = new WorkflowDefinitionId("acme", "orders", "1.0.0");

    private static FlowHttpConfig.ClientOverrideConfig override(String name) {
        FlowHttpConfig.ClientOverrideConfig cfg = mock(FlowHttpConfig.ClientOverrideConfig.class);
        when(cfg.name()).thenReturn(Optional.ofNullable(name));
        return cfg;
    }

    private RoutingNameResolver resolver(Map<String, FlowHttpConfig.ClientOverrideConfig> overrides) {
        FlowHttpConfig config = mock(FlowHttpConfig.class);
        when(config.workflow()).thenReturn(overrides);
        return new RoutingNameResolver(config);
    }

    @Test
    @DisplayName("task full key wins over all other levels")
    void task_full_wins() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("acme:orders:1.0.0.task.payment", override("taskFull"));
        overrides.put("acme:orders.task.payment", override("taskMedium"));
        overrides.put("orders.task.payment", override("taskShort"));
        overrides.put("acme:orders:1.0.0", override("wfFull"));
        overrides.put("acme:orders", override("wfMedium"));
        overrides.put("orders", override("wfShort"));

        assertThat(resolver(overrides).resolveName(WORKFLOW, "payment")).isEqualTo("taskFull");
    }

    @Test
    @DisplayName("task medium key wins when full is absent")
    void task_medium_wins() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("acme:orders.task.payment", override("taskMedium"));
        overrides.put("orders", override("wfShort"));

        assertThat(resolver(overrides).resolveName(WORKFLOW, "payment")).isEqualTo("taskMedium");
    }

    @Test
    @DisplayName("task short key wins when full and medium are absent")
    void task_short_wins() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("orders.task.payment", override("taskShort"));
        overrides.put("acme:orders:1.0.0", override("wfFull"));

        assertThat(resolver(overrides).resolveName(WORKFLOW, "payment")).isEqualTo("taskShort");
    }

    @Test
    @DisplayName("workflow full key wins when no task overrides")
    void workflow_full_wins() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("acme:orders:1.0.0", override("wfFull"));
        overrides.put("acme:orders", override("wfMedium"));
        overrides.put("orders", override("wfShort"));

        assertThat(resolver(overrides).resolveName(WORKFLOW, "payment")).isEqualTo("wfFull");
    }

    @Test
    @DisplayName("workflow medium key wins when full is absent")
    void workflow_medium_wins() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("acme:orders", override("wfMedium"));
        overrides.put("orders", override("wfShort"));

        assertThat(resolver(overrides).resolveName(WORKFLOW, "payment")).isEqualTo("wfMedium");
    }

    @Test
    @DisplayName("workflow short key is the last fallback")
    void workflow_short_wins() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("orders", override("wfShort"));

        assertThat(resolver(overrides).resolveName(WORKFLOW, "payment")).isEqualTo("wfShort");
    }

    @Test
    @DisplayName("returns null when no overrides match")
    void no_match_returns_null() {
        assertThat(resolver(Map.of()).resolveName(WORKFLOW, "payment")).isNull();
    }

    @Test
    @DisplayName("null task name skips task-level keys")
    void null_task_skips_task_keys() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("orders.task.payment", override("taskShort"));
        overrides.put("orders", override("wfShort"));

        assertThat(resolver(overrides).resolveName(WORKFLOW, null)).isEqualTo("wfShort");
    }

    @Test
    @DisplayName("blank task name skips task-level keys")
    void blank_task_skips_task_keys() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("orders", override("wfShort"));

        assertThat(resolver(overrides).resolveName(WORKFLOW, "  ")).isEqualTo("wfShort");
    }

    @Test
    @DisplayName("override with empty name is ignored")
    void override_with_empty_name_is_ignored() {
        Map<String, FlowHttpConfig.ClientOverrideConfig> overrides = new HashMap<>();
        overrides.put("orders", override(null));

        assertThat(resolver(overrides).resolveName(WORKFLOW, "payment")).isNull();
    }
}
