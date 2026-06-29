package io.quarkiverse.flow.oidc.config;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.flow.oidc")
public interface FlowOidcConfig {

    /**
     * Global token-exchange settings.
     */
    TokenExchangeConfig tokenExchange();

    /**
     * Subject-token extraction settings.
     */
    SubjectTokenConfig subjectToken();

    /**
     * Per-auth-scheme configuration, keyed by the named authentication the task references.
     */
    Map<String, AuthSchemeConfig> auth();

    interface TokenExchangeConfig {

        /**
         * Whether token exchange is enabled globally. When {@code false}, schemes fall back to
         * client-credentials unless they explicitly enable exchange or propagation.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * How many seconds before expiry a cached token is proactively refreshed.
         */
        @WithDefault("PT300S")
        Duration proactiveRefreshSeconds();

        /**
         * How often (seconds) the proactive-refresh monitor runs.
         */
        @WithDefault("PT60S")
        Duration monitorRateSeconds();
    }

    interface SubjectTokenConfig {

        /**
         * Workflow input key holding the subject token (for programmatic / non-HTTP triggers).
         */
        @WithDefault("subjectToken")
        String inputKey();

        /**
         * {@code SecurityIdentity} attribute holding the subject token (for HTTP-triggered workflows).
         */
        @WithDefault("access_token")
        String securityIdentityAttribute();
    }

    interface AuthSchemeConfig {

        /**
         * Name of the {@code quarkus.oidc-client.<name>} to use for exchange and client-credentials.
         * Defaults to the scheme name.
         */
        Optional<String> oidcClientName();

        /**
         * Force token exchange on/off for this scheme, overriding the global setting and smart default.
         */
        Optional<Boolean> tokenExchangeEnabled();

        /**
         * Per-scheme proactive-refresh threshold (seconds), overriding the global value.
         */
        Optional<Integer> proactiveRefreshSeconds();

        /**
         * Forward the caller's subject token unchanged. Takes precedence over exchange when enabled.
         */
        Optional<Boolean> tokenPropagationEnabled();
    }
}
