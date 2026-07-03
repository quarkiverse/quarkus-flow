package io.quarkiverse.flow.oidc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Resolves OIDC client configuration overrides for a given workflow and task.
 *
 * <p>
 * Resolution follows the same early-return pattern as
 * {@code GrpcChannelProvider.resolveClientName()}: the most specific match wins and is returned immediately, without
 * merging across layers.
 *
 * <p>
 * Resolution order (most to least specific):
 * <ol>
 * <li>{@code namespace:name:version:taskName} — task-level override</li>
 * <li>{@code namespace:name:version} — versioned workflow override</li>
 * <li>{@code namespace:name} — versionless workflow override</li>
 * <li>{@code authPolicyName} — named authentication policy (e.g. {@code FuncDSL.use("keycloak")})</li>
 * <li>Global defaults — root-level {@link FlowOidcConfig} properties</li>
 * </ol>
 */
public final class OidcConfigResolver {

    private static final String KEY_SEPARATOR = ":";

    private final FlowOidcConfig config;

    public OidcConfigResolver(FlowOidcConfig config) {
        this.config = config;
    }

    /**
     * Resolves the effective override for the given workflow, task, and named auth policy.
     *
     * @param workflowId the workflow identity
     * @param taskName the task name within the workflow (maybe {@code null} or blank)
     * @param authPolicyName the named authentication policy (maybe {@code null} or blank)
     * @return the resolved override from the most specific matching layer
     */
    public ResolvedOverride resolve(WorkflowDefinitionId workflowId, String taskName, String authPolicyName) {
        Map<String, OidcClientOverrideConfig> clients = config.client();

        String workflowKey = workflowId.toString(KEY_SEPARATOR);

        // 1. Task-level override: namespace:name:version:taskName
        if (taskName != null && !taskName.isBlank()) {
            OidcClientOverrideConfig taskOverride = clients.get(workflowKey + KEY_SEPARATOR + taskName);
            if (taskOverride != null) {
                return toResolvedOverride(taskOverride);
            }
        }

        // 2. Versioned workflow override: namespace:name:version
        OidcClientOverrideConfig versioned = clients.get(workflowKey);
        if (versioned != null) {
            return toResolvedOverride(versioned);
        }

        // 3. Versionless workflow override: namespace:name
        String versionlessKey = workflowId.namespace() + KEY_SEPARATOR + workflowId.name();
        OidcClientOverrideConfig versionless = clients.get(versionlessKey);
        if (versionless != null) {
            return toResolvedOverride(versionless);
        }

        // 4. Named authentication policy (e.g. use("keycloak-prod"))
        if (authPolicyName != null && !authPolicyName.isBlank()) {
            OidcClientOverrideConfig named = clients.get(authPolicyName);
            if (named != null) {
                return toResolvedOverride(named);
            }
        }

        // 5. Global defaults (root-level FlowOidcConfig properties)
        return toResolvedOverride(config);
    }

    static ResolvedOverride toResolvedOverride(OidcClientOverrideConfig source) {
        return new ResolvedOverride(
                source.authServerUrl(),
                source.tokenPath(),
                source.discoveryEnabled(),
                source.clientId(),
                source.clientSecret(),
                source.clientSecretMethod(),
                source.grantType(),
                source.scopes(),
                source.audience(),
                source.accessTokenExpiresIn(),
                source.accessTokenExpirySkew(),
                source.refreshTokenTimeSkew(),
                source.absoluteExpiresIn(),
                source.earlyTokensAcquisition(),
                source.headers() != null ? Map.copyOf(source.headers()) : Map.of(),
                source.refreshInterval());
    }

    /**
     * The resolved OIDC client override from the most specific matching layer.
     */
    public record ResolvedOverride(
            Optional<String> authServerUrl,
            Optional<String> tokenPath,
            Optional<Boolean> discoveryEnabled,
            Optional<String> clientId,
            Optional<String> clientSecret,
            Optional<String> clientSecretMethod,
            Optional<String> grantType,
            Optional<List<String>> scopes,
            Optional<List<String>> audience,
            Optional<Duration> accessTokenExpiresIn,
            Optional<Duration> accessTokenExpirySkew,
            Optional<Duration> refreshTokenTimeSkew,
            Optional<Boolean> absoluteExpiresIn,
            Optional<Boolean> earlyTokensAcquisition,
            Map<String, String> headers,
            Optional<Duration> refreshInterval) {

        public boolean isEmpty() {
            return authServerUrl.isEmpty()
                    && tokenPath.isEmpty()
                    && discoveryEnabled.isEmpty()
                    && clientId.isEmpty()
                    && clientSecret.isEmpty()
                    && clientSecretMethod.isEmpty()
                    && grantType.isEmpty()
                    && scopes.isEmpty()
                    && audience.isEmpty()
                    && accessTokenExpiresIn.isEmpty()
                    && accessTokenExpirySkew.isEmpty()
                    && refreshTokenTimeSkew.isEmpty()
                    && absoluteExpiresIn.isEmpty()
                    && earlyTokensAcquisition.isEmpty()
                    && headers.isEmpty()
                    && refreshInterval.isEmpty();
        }
    }
}
