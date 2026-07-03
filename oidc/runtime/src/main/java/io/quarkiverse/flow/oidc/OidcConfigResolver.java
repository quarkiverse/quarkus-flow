package io.quarkiverse.flow.oidc;

import java.util.Map;
import java.util.Optional;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Resolves the Quarkus OIDC client name for a given workflow and task.
 *
 * <p>
 * Resolution follows the same early-return pattern as {@code GrpcChannelProvider.resolveClientName()}: the most
 * specific match wins and is returned immediately.
 *
 * <p>
 * Resolution order (most to least specific):
 * <ol>
 * <li>{@code namespace:name:version:taskName} — task-level override</li>
 * <li>{@code namespace:name:version} — versioned workflow override</li>
 * <li>{@code namespace:name} — versionless workflow override</li>
 * <li>{@code authPolicyName} — named authentication policy (e.g. {@code use("keycloak-prod")})</li>
 * </ol>
 * If no override matches, the DSL-derived client is used as-is.
 */
public final class OidcConfigResolver {

    private static final String KEY_SEPARATOR = ":";

    private final FlowOidcConfig config;

    public OidcConfigResolver(FlowOidcConfig config) {
        this.config = config;
    }

    /**
     * Resolves the Quarkus OIDC client name for the given workflow, task, and named auth policy.
     *
     * @param workflowId the workflow identity
     * @param taskName the task name within the workflow (may be {@code null} or blank)
     * @param authPolicyName the named authentication policy (may be {@code null} or blank)
     * @return the resolved client name, or empty if no override matches (fall back to DSL)
     */
    public Optional<String> resolve(WorkflowDefinitionId workflowId, String taskName, String authPolicyName) {
        Map<String, FlowOidcConfig.ClientOverrideConfig> clients = config.client();

        String workflowKey = workflowId.toString(KEY_SEPARATOR);

        // 1. Task-level override: namespace:name:version:taskName
        if (taskName != null && !taskName.isBlank()) {
            String name = overrideName(clients, workflowKey + KEY_SEPARATOR + taskName);
            if (name != null) {
                return Optional.of(name);
            }
        }

        // 2. Versioned workflow override: namespace:name:version
        String versionedName = overrideName(clients, workflowKey);
        if (versionedName != null) {
            return Optional.of(versionedName);
        }

        // 3. Versionless workflow override: namespace:name
        String versionlessKey = workflowId.namespace() + KEY_SEPARATOR + workflowId.name();
        String versionlessName = overrideName(clients, versionlessKey);
        if (versionlessName != null) {
            return Optional.of(versionlessName);
        }

        // 4. Named authentication policy (e.g. use("keycloak-prod"))
        if (authPolicyName != null && !authPolicyName.isBlank()) {
            String namedName = overrideName(clients, authPolicyName);
            if (namedName != null) {
                return Optional.of(namedName);
            }
        }

        // 5. No override — DSL fallback
        return Optional.empty();
    }

    private static String overrideName(Map<String, FlowOidcConfig.ClientOverrideConfig> overrides, String key) {
        FlowOidcConfig.ClientOverrideConfig override = overrides.get(key);
        if (override != null && override.name().isPresent()) {
            return override.name().get();
        }
        return null;
    }
}
