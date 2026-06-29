package io.quarkiverse.flow.oidc.config;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.oidc.AuthenticationMode;
import io.quarkiverse.flow.oidc.config.FlowOidcConfig.AuthSchemeConfig;

/**
 * Derives the effective {@link AuthenticationMode} strategy for an auth scheme from configuration, since the
 * scheme carries no explicit mode. Precedence: per-auth-scheme &gt; global &gt; smart default.
 *
 * <ul>
 * <li>{@code token-propagation-enabled=true} ⇒ {@link AuthenticationMode#TOKEN_PROPAGATION} (wins over exchange);</li>
 * <li>otherwise exchange when {@link #isTokenExchangeEnabled} holds ⇒ {@link AuthenticationMode#TOKEN_EXCHANGE};</li>
 * <li>otherwise {@link AuthenticationMode#CLIENT_CREDENTIALS}.</li>
 * </ul>
 */
@ApplicationScoped
public class AuthConfigResolver {

    private final FlowOidcConfig config;

    @Inject
    public AuthConfigResolver(FlowOidcConfig config) {
        this.config = config;
    }

    public AuthenticationMode resolveMode(String schemeName, boolean subjectTokenAvailable) {
        if (isTokenPropagationEnabled(schemeName)) {
            return AuthenticationMode.TOKEN_PROPAGATION;
        }
        if (isTokenExchangeEnabled(schemeName, subjectTokenAvailable)) {
            return AuthenticationMode.TOKEN_EXCHANGE;
        }
        return AuthenticationMode.CLIENT_CREDENTIALS;
    }

    /**
     * Determine whether token exchange applies for this scheme. A per-scheme {@code token-exchange-enabled}
     * value forces exchange on/off; otherwise the smart default applies — global exchange only kicks in when
     * a subject token is actually available, so subject-less schemes fall back to client-credentials.
     */
    public boolean isTokenExchangeEnabled(String schemeName, boolean subjectTokenAvailable) {
        Optional<Boolean> schemeConfig = scheme(schemeName).flatMap(AuthSchemeConfig::tokenExchangeEnabled);
        return schemeConfig.orElseGet(() -> config.tokenExchange().enabled() && subjectTokenAvailable);
    }

    /**
     * Determine whether token propagation applies for this scheme. Propagation takes precedence over exchange.
     */
    public boolean isTokenPropagationEnabled(String schemeName) {
        return scheme(schemeName).flatMap(AuthSchemeConfig::tokenPropagationEnabled).orElse(false);
    }

    private Optional<AuthSchemeConfig> scheme(String schemeName) {
        return Optional.ofNullable(config.auth().get(schemeName));
    }
}
