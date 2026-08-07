package io.quarkiverse.flow.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

class ClientConfigCascadeTest {

    private static final WorkflowDefinitionId ID = new WorkflowDefinitionId("acme", "orders", "1.0.0");

    @Test
    @DisplayName("task full key wins over all other levels")
    void task_full_wins() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("acme:orders:1.0.0.task.payment", "taskFull");
        overrides.put("acme:orders.task.payment", "taskMedium");
        overrides.put("orders.task.payment", "taskShort");
        overrides.put("acme:orders:1.0.0", "wfFull");
        overrides.put("acme:orders", "wfMedium");
        overrides.put("orders", "wfShort");

        String result = ClientConfigCascade.resolve(overrides::get, ID, "payment");

        assertThat(result).isEqualTo("taskFull");
    }

    @Test
    @DisplayName("task medium key wins when full is absent")
    void task_medium_wins() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("acme:orders.task.payment", "taskMedium");
        overrides.put("orders.task.payment", "taskShort");
        overrides.put("acme:orders:1.0.0", "wfFull");

        String result = ClientConfigCascade.resolve(overrides::get, ID, "payment");

        assertThat(result).isEqualTo("taskMedium");
    }

    @Test
    @DisplayName("task short key wins when full and medium are absent")
    void task_short_wins() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("orders.task.payment", "taskShort");
        overrides.put("acme:orders:1.0.0", "wfFull");

        String result = ClientConfigCascade.resolve(overrides::get, ID, "payment");

        assertThat(result).isEqualTo("taskShort");
    }

    @Test
    @DisplayName("workflow full key wins when no task overrides exist")
    void workflow_full_wins() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("acme:orders:1.0.0", "wfFull");
        overrides.put("acme:orders", "wfMedium");
        overrides.put("orders", "wfShort");

        String result = ClientConfigCascade.resolve(overrides::get, ID, "payment");

        assertThat(result).isEqualTo("wfFull");
    }

    @Test
    @DisplayName("workflow medium key wins when full is absent")
    void workflow_medium_wins() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("acme:orders", "wfMedium");
        overrides.put("orders", "wfShort");

        String result = ClientConfigCascade.resolve(overrides::get, ID, "payment");

        assertThat(result).isEqualTo("wfMedium");
    }

    @Test
    @DisplayName("workflow short key is the last fallback")
    void workflow_short_wins() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("orders", "wfShort");

        String result = ClientConfigCascade.resolve(overrides::get, ID, "payment");

        assertThat(result).isEqualTo("wfShort");
    }

    @Test
    @DisplayName("returns null when nothing matches")
    void no_match_returns_null() {
        String result = ClientConfigCascade.resolve(key -> null, ID, "payment");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("null task name skips task-level keys")
    void null_task_name_skips_task_keys() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("acme:orders:1.0.0.task.payment", "taskFull");
        overrides.put("acme:orders:1.0.0", "wfFull");

        String result = ClientConfigCascade.resolve(overrides::get, ID, null);

        assertThat(result).isEqualTo("wfFull");
    }

    @Test
    @DisplayName("blank task name skips task-level keys")
    void blank_task_name_skips_task_keys() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("acme:orders:1.0.0.task.payment", "taskFull");
        overrides.put("orders", "wfShort");

        String result = ClientConfigCascade.resolve(overrides::get, ID, "  ");

        assertThat(result).isEqualTo("wfShort");
    }

    @Test
    @DisplayName("workflow-level keys work without task name")
    void workflow_only_resolution() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("acme:orders", "wfMedium");

        String result = ClientConfigCascade.resolve(overrides::get, ID, null);

        assertThat(result).isEqualTo("wfMedium");
    }
}
