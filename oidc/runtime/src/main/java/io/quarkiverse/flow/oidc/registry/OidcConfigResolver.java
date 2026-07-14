package io.quarkiverse.flow.oidc.registry;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.oidc.FlowOidcConfig;
import io.quarkiverse.flow.oidc.OidcNamingConvention;
import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.client.runtime.OidcClientsConfig;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Resolves the Quarkus OIDC client name for a given workflow and task.
 *
 * <p>
 * Resolution follows progressive specificity per ADR 2026-07-07 Unified Client Naming Pattern.
 *
 * <p>
 * Resolution order (most to least specific):
 * <ol>
 * <li>{@code namespace:name:version.task.taskName} — task-level full</li>
 * <li>{@code namespace:name.task.taskName} — task-level medium</li>
 * <li>{@code name.task.taskName} — task-level short</li>
 * <li>{@code namespace:name:version} — workflow-level full</li>
 * <li>{@code namespace:name} — workflow-level medium</li>
 * <li>{@code name} — workflow-level short</li>
 * <li>{@code authPolicyName} — named authentication policy (e.g. {@code use("keycloak")})</li>
 * </ol>
 * If no override matches, the DSL-derived client name is used.
 */
@ApplicationScoped
@Unremovable
public final class OidcConfigResolver {

    private final FlowOidcConfig config;
    private final OidcClientsConfig oidcClientsConfig;

    @Inject
    public OidcConfigResolver(FlowOidcConfig config, OidcClientsConfig oidcClientsConfig) {
        this.config = config;
        this.oidcClientsConfig = oidcClientsConfig;
    }

    private static void validateClientName(String clientName) {
        if (clientName == null || clientName.isBlank()) {
            throw new IllegalStateException("Flow OIDC: resolved client name cannot be null or blank");
        }
    }

    private static String overrideName(Map<String, FlowOidcConfig.ClientOverrideConfig> overrides, String key) {
        FlowOidcConfig.ClientOverrideConfig override = overrides.get(key);
        if (override != null && override.name().isPresent()) {
            return override.name().get();
        }
        return null;
    }

    private static Duration overrideCreationTimeout(Map<String, FlowOidcConfig.ClientOverrideConfig> overrides, String key) {
        FlowOidcConfig.ClientOverrideConfig override = overrides.get(key);
        return override != null ? override.creationTimeout() : null;
    }

    /**
     * Resolves the Quarkus OIDC client name for the given workflow, task, and named auth policy.
     *
     * @param workflowId the workflow identity
     * @param taskName the task name within the workflow (maybe {@code null} or blank)
     * @param authPolicyName the named authentication policy (maybe {@code null} or blank)
     * @return the resolved client name, or empty if no override matches (fall back to DSL)
     */
    public Optional<String> resolve(WorkflowDefinitionId workflowId, String taskName, String authPolicyName) {
        Map<String, FlowOidcConfig.ClientOverrideConfig> clients = config.client();

        // Task-level overrides (progressive specificity)
        if (taskName != null && !taskName.isBlank()) {
            // 1. Task-level full: namespace:name:version.task.taskName
            String taskFull = overrideName(clients, OidcNamingConvention.clientName(workflowId, taskName));
            if (taskFull != null) {
                validateClientName(taskFull);
                return Optional.of(taskFull);
            }

            // 2. Task-level medium: namespace:name.task.taskName
            String taskMedium = overrideName(clients, OidcNamingConvention.taskConfigKeyMedium(workflowId, taskName));
            if (taskMedium != null) {
                validateClientName(taskMedium);
                return Optional.of(taskMedium);
            }

            // 3. Task-level short: name.task.taskName
            String taskShort = overrideName(clients, OidcNamingConvention.taskConfigKeyShort(workflowId, taskName));
            if (taskShort != null) {
                validateClientName(taskShort);
                return Optional.of(taskShort);
            }
        }

        // Workflow-level overrides (progressive specificity)
        // 4. Workflow-level full: namespace:name:version
        String workflowFull = overrideName(clients, OidcNamingConvention.workflowConfigKeyFull(workflowId));
        if (workflowFull != null) {
            validateClientName(workflowFull);
            return Optional.of(workflowFull);
        }

        // 5. Workflow-level medium: namespace:name
        String workflowMedium = overrideName(clients, OidcNamingConvention.workflowConfigKeyMedium(workflowId));
        if (workflowMedium != null) {
            validateClientName(workflowMedium);
            return Optional.of(workflowMedium);
        }

        // 6. Workflow-level short: name
        String workflowShort = overrideName(clients, OidcNamingConvention.workflowConfigKeyShort(workflowId));
        if (workflowShort != null) {
            validateClientName(workflowShort);
            return Optional.of(workflowShort);
        }

        // 7. Named authentication policy (e.g. use("keycloak"))
        if (authPolicyName != null && !authPolicyName.isBlank()) {
            String namedName = overrideName(clients, authPolicyName);
            if (namedName != null) {
                validateClientName(namedName);
                return Optional.of(namedName);
            }
        }

        // 8. No override — DSL fallback
        return Optional.empty();
    }

    /**
     * Resolves the OIDC client-creation timeout for the given workflow, task, and named auth policy, following the same
     * most-specific-wins cascade as {@link #resolve}.
     *
     * @param workflowId the workflow identity
     * @param taskName the task name within the workflow (maybe {@code null} or blank)
     * @param authPolicyName the named authentication policy (maybe {@code null} or blank)
     * @return the most specific overridden creation timeout, or the global {@code quarkus.flow.oidc.creation-timeout}
     */
    public Duration resolveCreationTimeout(WorkflowDefinitionId workflowId, String taskName, String authPolicyName) {
        Map<String, FlowOidcConfig.ClientOverrideConfig> clients = config.client();

        // Task-level overrides (progressive specificity)
        if (taskName != null && !taskName.isBlank()) {
            // 1. Task-level full
            Duration taskFull = overrideCreationTimeout(clients, OidcNamingConvention.clientName(workflowId, taskName));
            if (taskFull != null) {
                return taskFull;
            }

            // 2. Task-level medium
            Duration taskMedium = overrideCreationTimeout(clients,
                    OidcNamingConvention.taskConfigKeyMedium(workflowId, taskName));
            if (taskMedium != null) {
                return taskMedium;
            }

            // 3. Task-level short
            Duration taskShort = overrideCreationTimeout(clients,
                    OidcNamingConvention.taskConfigKeyShort(workflowId, taskName));
            if (taskShort != null) {
                return taskShort;
            }
        }

        // Workflow-level overrides (progressive specificity)
        // 4. Workflow-level full
        Duration workflowFull = overrideCreationTimeout(clients, OidcNamingConvention.workflowConfigKeyFull(workflowId));
        if (workflowFull != null) {
            return workflowFull;
        }

        // 5. Workflow-level medium
        Duration workflowMedium = overrideCreationTimeout(clients, OidcNamingConvention.workflowConfigKeyMedium(workflowId));
        if (workflowMedium != null) {
            return workflowMedium;
        }

        // 6. Workflow-level short
        Duration workflowShort = overrideCreationTimeout(clients, OidcNamingConvention.workflowConfigKeyShort(workflowId));
        if (workflowShort != null) {
            return workflowShort;
        }

        // 7. Named authentication policy
        if (authPolicyName != null && !authPolicyName.isBlank()) {
            Duration namedTimeout = overrideCreationTimeout(clients, authPolicyName);
            if (namedTimeout != null) {
                return namedTimeout;
            }
        }

        // 8. No override — global default
        return config.creationTimeout();
    }

    /**
     * Resolves the connection timeout for token negotiation with the given OIDC client.
     * <p>
     * For user-configured Quarkus OIDC clients (quarkus.oidc-client.&lt;name&gt;), uses that client's
     * connection-timeout if configured. Otherwise, falls back to the global Flow OIDC connection timeout.
     *
     * @param clientName the OIDC client name
     * @return the connection timeout to use when awaiting token negotiation
     */
    public Duration namedConnectionTimeout(String clientName) {
        final OidcClientConfig named = oidcClientsConfig.namedClients().get(clientName);
        return named != null && named.connectionTimeout() != null
                ? named.connectionTimeout()
                : config.connectionTimeout();
    }
}
