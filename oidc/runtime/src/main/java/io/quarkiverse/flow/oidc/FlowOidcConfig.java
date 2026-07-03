package io.quarkiverse.flow.oidc;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration for the Quarkus Flow OIDC integration.
 */
@ConfigMapping(prefix = "quarkus.flow.oidc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowOidcConfig {

    /**
     * Whether OAuth2/OIDC token negotiation should be delegated to {@code quarkus-oidc-client}. When disabled, the
     * Serverless Workflow SDK falls back to its own token negotiation.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Per-workflow/task OIDC client routing overrides, keyed by composite identifier.
     * <p>
     * Keys can be:
     * <ul>
     * <li>{@code <namespace>:<name>:<version>:<taskName>} — task-level override</li>
     * <li>{@code <namespace>:<name>:<version>} — workflow-level override</li>
     * <li>{@code <namespace>:<name>} — versionless override, applied to all versions of the workflow</li>
     * <li>{@code <authPolicyName>} — named authentication policy override</li>
     * </ul>
     */
    Map<String, ClientOverrideConfig> client();

    /**
     * Override for the Quarkus OIDC client name.
     */
    interface ClientOverrideConfig {

        /**
         * The Quarkus OIDC client name to use, configured under {@code quarkus.oidc-client.<name>}.
         */
        Optional<String> name();
    }
}
