package io.quarkiverse.flow.oidc;

import java.time.Duration;

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
     * The maximum amount of time to wait for the access-token request to the authorization server to complete when a
     * workflow OAuth2/OIDC policy negotiates a token.
     */
    @WithDefault("10S")
    Duration requestTimeout();
}
