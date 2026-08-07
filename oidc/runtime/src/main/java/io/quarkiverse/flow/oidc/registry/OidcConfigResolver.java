package io.quarkiverse.flow.oidc.registry;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.config.ClientConfigCascade;
import io.quarkiverse.flow.oidc.FlowOidcConfig;
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

    private static Duration overrideConnectionTimeout(Map<String, FlowOidcConfig.ClientOverrideConfig> overrides, String key) {
        FlowOidcConfig.ClientOverrideConfig override = overrides.get(key);
        return override != null ? override.connectionTimeout() : null;
    }

    /**
     * Resolves the Quarkus OIDC client name for the given workflow, task, and named auth policy.
     *
     * @param workflowId the workflow identity
     * @param taskName the task name within the workflow (maybe {@code null} or blank)
     * @param authPolicyName the named authentication policy (maybe {@code null} or blank)
     * @return the resolved client name, or empty if no override matches (fall back to DSL)
     */
    public Optional<String> resolveOidcClientName(WorkflowDefinitionId workflowId, String taskName, String authPolicyName) {
        Map<String, FlowOidcConfig.ClientOverrideConfig> clients = config.client();

        // 6-level cascade (task full → task medium → task short → workflow full → workflow medium → workflow short)
        String cascadeResult = ClientConfigCascade.resolve(key -> overrideName(clients, key), workflowId, taskName);
        if (cascadeResult != null) {
            validateClientName(cascadeResult);
            return Optional.of(cascadeResult);
        }

        // Level 7: Named authentication policy (OIDC-specific)
        if (authPolicyName != null && !authPolicyName.isBlank()) {
            String namedName = overrideName(clients, authPolicyName);
            if (namedName != null) {
                validateClientName(namedName);
                return Optional.of(namedName);
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves the OIDC client-creation timeout for the given workflow, task, and named auth policy, following the same
     * most-specific-wins cascade as {@link #resolveOidcClientName}.
     *
     * @param workflowId the workflow identity
     * @param taskName the task name within the workflow (maybe {@code null} or blank)
     * @param authPolicyName the named authentication policy (maybe {@code null} or blank)
     * @return the most specific overridden creation timeout, or the global {@code quarkus.flow.oidc.creation-timeout}
     */
    public Duration resolveCreationTimeout(WorkflowDefinitionId workflowId, String taskName, String authPolicyName) {
        Map<String, FlowOidcConfig.ClientOverrideConfig> clients = config.client();

        // 6-level cascade
        Duration cascadeResult = ClientConfigCascade.resolve(key -> overrideCreationTimeout(clients, key), workflowId,
                taskName);
        if (cascadeResult != null) {
            return cascadeResult;
        }

        // Level 7: Named authentication policy
        if (authPolicyName != null && !authPolicyName.isBlank()) {
            Duration namedTimeout = overrideCreationTimeout(clients, authPolicyName);
            if (namedTimeout != null) {
                return namedTimeout;
            }
        }

        return config.creationTimeout();
    }

    /**
     * Resolves the OIDC connection timeout for the given workflow, task, and named auth policy, following the same
     * most-specific-wins cascade as {@link #resolveOidcClientName}.
     * <p>
     * <b>Note:</b> For named clients (routed via {@code quarkus.flow.oidc.client.<key>.name}), the named client's
     * own {@code connection-timeout} takes precedence over any Flow OIDC connection timeout override.
     *
     * @param workflowId the workflow identity
     * @param taskName the task name within the workflow (maybe {@code null} or blank)
     * @param authPolicyName the named authentication policy (maybe {@code null} or blank)
     * @return the most specific overridden connection timeout, or the global {@code quarkus.flow.oidc.connection-timeout}
     */
    public Duration resolveConnectionTimeout(WorkflowDefinitionId workflowId, String taskName, String authPolicyName) {
        Map<String, FlowOidcConfig.ClientOverrideConfig> clients = config.client();

        // 6-level cascade
        Duration cascadeResult = ClientConfigCascade.resolve(key -> overrideConnectionTimeout(clients, key), workflowId,
                taskName);
        if (cascadeResult != null) {
            return cascadeResult;
        }

        // Level 7: Named authentication policy
        if (authPolicyName != null && !authPolicyName.isBlank()) {
            Duration namedTimeout = overrideConnectionTimeout(clients, authPolicyName);
            if (namedTimeout != null) {
                return namedTimeout;
            }
        }

        return config.connectionTimeout();
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
        if (clientName == null)
            return config.connectionTimeout();
        final OidcClientConfig named = oidcClientsConfig.namedClients().get(clientName);
        return named != null && named.connectionTimeout() != null
                ? named.connectionTimeout()
                : config.connectionTimeout();
    }
}
