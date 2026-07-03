package io.quarkiverse.flow.oidc;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration for the Quarkus Flow OIDC integration.
 *
 * <p>
 * Root-level properties (inherited from {@link OidcClientOverrideConfig}) serve as global defaults for all OIDC clients
 * created by Flow. Per-workflow/task overrides are configured in the {@link #client()} map, keyed by composite
 * identifier.
 */
@ConfigMapping(prefix = "quarkus.flow.oidc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowOidcConfig extends OidcClientOverrideConfig {

    /**
     * Whether OAuth2/OIDC token negotiation should be delegated to {@code quarkus-oidc-client}. When disabled, the
     * Serverless Workflow SDK falls back to its own token negotiation.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Per-workflow/task OIDC client overrides, keyed by composite identifier.
     * <p>
     * Keys can be:
     * <ul>
     * <li>{@code <namespace>:<name>:<version>:<taskName>} — task-level</li>
     * <li>{@code <namespace>:<name>:<version>} — workflow-level</li>
     * <li>{@code <namespace>:<name>} — versionless (all versions)</li>
     * </ul>
     */
    Map<String, OidcClientOverrideConfig> client();
}