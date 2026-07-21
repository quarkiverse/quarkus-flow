package io.quarkiverse.flow.oidc.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.oidc.FlowOidcConfig;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.client.runtime.OidcClientsConfig;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

class OidcConfigResolverTest {

    private FlowOidcConfig flowOidcConfig;
    private OidcClientsConfig oidcClientsConfig;
    private OidcConfigResolver resolver;

    private Map<String, FlowOidcConfig.ClientOverrideConfig> clientOverrides;
    private Map<String, OidcClientConfig> namedClients;

    @BeforeEach
    void setUp() {
        flowOidcConfig = mock(FlowOidcConfig.class);
        oidcClientsConfig = mock(OidcClientsConfig.class);

        clientOverrides = new HashMap<>();
        namedClients = new HashMap<>();

        when(flowOidcConfig.client()).thenReturn(clientOverrides);
        when(oidcClientsConfig.namedClients()).thenReturn(namedClients);
        when(flowOidcConfig.creationTimeout()).thenReturn(Duration.ofSeconds(10));
        when(flowOidcConfig.connectionTimeout()).thenReturn(Duration.ofSeconds(10));

        resolver = new OidcConfigResolver(flowOidcConfig, oidcClientsConfig);
    }

    // ========== Connection Timeout Resolution Tests ==========

    @Test
    @DisplayName("resolveConnectionTimeout returns global default when no overrides")
    void test_resolveConnectionTimeout_global_default() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("resolveConnectionTimeout uses task-level full override (highest priority)")
    void test_resolveConnectionTimeout_task_level_full() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");
        String taskName = "my-task";

        // Set up all levels to verify task-level full wins
        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20)); // workflow short
        addConnectionTimeoutOverride("test-ns:test-workflow", Duration.ofSeconds(25)); // workflow medium
        addConnectionTimeoutOverride("test-ns:test-workflow:1.0.0", Duration.ofSeconds(30)); // workflow full
        addConnectionTimeoutOverride("test-workflow.task.my-task", Duration.ofSeconds(35)); // task short
        addConnectionTimeoutOverride("test-ns:test-workflow.task.my-task", Duration.ofSeconds(40)); // task medium
        addConnectionTimeoutOverride("test-ns:test-workflow:1.0.0.task.my-task", Duration.ofSeconds(45)); // task full ← wins

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, taskName, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    @DisplayName("resolveConnectionTimeout falls back to task-level medium when full not present")
    void test_resolveConnectionTimeout_task_level_medium() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");
        String taskName = "my-task";

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20));
        addConnectionTimeoutOverride("test-ns:test-workflow.task.my-task", Duration.ofSeconds(40)); // ← wins

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, taskName, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    @DisplayName("resolveConnectionTimeout falls back to task-level short when medium not present")
    void test_resolveConnectionTimeout_task_level_short() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");
        String taskName = "my-task";

        addConnectionTimeoutOverride("test-workflow.task.my-task", Duration.ofSeconds(35)); // ← wins

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, taskName, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(35));
    }

    @Test
    @DisplayName("resolveConnectionTimeout uses workflow-level full when no task overrides")
    void test_resolveConnectionTimeout_workflow_level_full() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20));
        addConnectionTimeoutOverride("test-ns:test-workflow", Duration.ofSeconds(25));
        addConnectionTimeoutOverride("test-ns:test-workflow:1.0.0", Duration.ofSeconds(30)); // ← wins

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("resolveConnectionTimeout uses workflow-level medium when full not present")
    void test_resolveConnectionTimeout_workflow_level_medium() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20));
        addConnectionTimeoutOverride("test-ns:test-workflow", Duration.ofSeconds(25)); // ← wins

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(25));
    }

    @Test
    @DisplayName("resolveConnectionTimeout uses workflow-level short when medium not present")
    void test_resolveConnectionTimeout_workflow_level_short() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20)); // ← wins

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("resolveConnectionTimeout uses named policy override")
    void test_resolveConnectionTimeout_named_policy() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");
        String authPolicyName = "my-keycloak-policy";

        addConnectionTimeoutOverride(authPolicyName, Duration.ofSeconds(50));

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, authPolicyName);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(50));
    }

    @Test
    @DisplayName("resolveConnectionTimeout task-level beats named policy")
    void test_resolveConnectionTimeout_task_beats_named_policy() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");
        String taskName = "my-task";
        String authPolicyName = "my-policy";

        addConnectionTimeoutOverride("test-workflow.task.my-task", Duration.ofSeconds(35)); // task short
        addConnectionTimeoutOverride(authPolicyName, Duration.ofSeconds(50)); // named policy

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, taskName, authPolicyName);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(35)); // task wins
    }

    @Test
    @DisplayName("resolveConnectionTimeout workflow-level beats named policy")
    void test_resolveConnectionTimeout_workflow_beats_named_policy() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");
        String authPolicyName = "my-policy";

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20)); // workflow short
        addConnectionTimeoutOverride(authPolicyName, Duration.ofSeconds(50)); // named policy

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, authPolicyName);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(20)); // workflow wins
    }

    @Test
    @DisplayName("resolveConnectionTimeout handles null task name")
    void test_resolveConnectionTimeout_null_task_name() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20));

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("resolveConnectionTimeout handles blank task name")
    void test_resolveConnectionTimeout_blank_task_name() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20));

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, "", null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("resolveConnectionTimeout handles null auth policy name")
    void test_resolveConnectionTimeout_null_auth_policy() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20));

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("resolveConnectionTimeout handles blank auth policy name")
    void test_resolveConnectionTimeout_blank_auth_policy() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        addConnectionTimeoutOverride("test-workflow", Duration.ofSeconds(20));

        Duration timeout = resolver.resolveConnectionTimeout(workflowId, null, "");

        assertThat(timeout).isEqualTo(Duration.ofSeconds(20));
    }

    // ========== Creation Timeout Resolution Tests (verify existing behavior) ==========

    @Test
    @DisplayName("resolveCreationTimeout returns global default when no overrides")
    void test_resolveCreationTimeout_global_default() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");

        Duration timeout = resolver.resolveCreationTimeout(workflowId, null, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("resolveCreationTimeout uses task-level full override")
    void test_resolveCreationTimeout_task_level_full() {
        WorkflowDefinitionId workflowId = createWorkflowId("test-ns", "test-workflow", "1.0.0");
        String taskName = "my-task";

        addCreationTimeoutOverride("test-ns:test-workflow:1.0.0.task.my-task", Duration.ofSeconds(45));

        Duration timeout = resolver.resolveCreationTimeout(workflowId, taskName, null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(45));
    }

    // ========== Named Client Connection Timeout Tests ==========

    @Test
    @DisplayName("namedConnectionTimeout returns named client timeout when configured")
    void test_namedConnectionTimeout_uses_named_client() {
        String clientName = "my-keycloak-client";
        OidcClientConfig clientConfig = mock(OidcClientConfig.class);
        when(clientConfig.connectionTimeout()).thenReturn(Duration.ofSeconds(30));

        namedClients.put(clientName, clientConfig);

        Duration timeout = resolver.namedConnectionTimeout(clientName);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("namedConnectionTimeout falls back to global when named client has no timeout")
    void test_namedConnectionTimeout_fallback_when_no_client_timeout() {
        String clientName = "my-keycloak-client";
        OidcClientConfig clientConfig = mock(OidcClientConfig.class);
        when(clientConfig.connectionTimeout()).thenReturn(null);

        namedClients.put(clientName, clientConfig);

        Duration timeout = resolver.namedConnectionTimeout(clientName);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(10)); // global fallback
    }

    @Test
    @DisplayName("namedConnectionTimeout falls back to global when client not found")
    void test_namedConnectionTimeout_fallback_when_client_not_found() {
        Duration timeout = resolver.namedConnectionTimeout("non-existent-client");

        assertThat(timeout).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("namedConnectionTimeout falls back to global when client name is null")
    void test_namedConnectionTimeout_fallback_when_null() {
        Duration timeout = resolver.namedConnectionTimeout(null);

        assertThat(timeout).isEqualTo(Duration.ofSeconds(10));
    }

    // ========== Helper Methods ==========

    private WorkflowDefinitionId createWorkflowId(String namespace, String name, String version) {
        Workflow workflow = FlowWorkflowBuilder.workflow(name, namespace, version).build();
        return WorkflowDefinitionId.of(workflow);
    }

    private void addConnectionTimeoutOverride(String key, Duration timeout) {
        FlowOidcConfig.ClientOverrideConfig config = mock(FlowOidcConfig.ClientOverrideConfig.class);
        when(config.connectionTimeout()).thenReturn(timeout);
        when(config.creationTimeout()).thenReturn(Duration.ofSeconds(10)); // default
        when(config.name()).thenReturn(Optional.empty());
        clientOverrides.put(key, config);
    }

    private void addCreationTimeoutOverride(String key, Duration timeout) {
        FlowOidcConfig.ClientOverrideConfig config = mock(FlowOidcConfig.ClientOverrideConfig.class);
        when(config.creationTimeout()).thenReturn(timeout);
        when(config.connectionTimeout()).thenReturn(Duration.ofSeconds(10)); // default
        when(config.name()).thenReturn(Optional.empty());
        clientOverrides.put(key, config);
    }
}
