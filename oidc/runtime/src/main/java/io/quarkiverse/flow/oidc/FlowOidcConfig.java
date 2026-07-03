package io.quarkiverse.flow.oidc;

import java.time.Duration;
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
     * The number of seconds after which the current OIDC client creation times out.
     */
    @WithDefault("10s")
    Duration creationTimeout();

    /**
     * The number of seconds after which the current OIDC connection request times out.
     */
    @WithDefault("10s")
    Duration connectionTimeout();

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
     * Routes a workflow, task or named authentication policy to a pre-configured Quarkus OIDC client.
     */
    interface ClientOverrideConfig {

        /**
         * The Quarkus OIDC client name to use, configured under {@code quarkus.oidc-client.<name>}. That client's own
         * configuration governs the token endpoint and its request timeouts.
         */
        Optional<String> name();

        /**
         * The number of seconds after which the current OIDC client creation times out.
         */
        @WithDefault("10s")
        Duration creationTimeout();
    }
}
