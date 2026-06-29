package io.quarkiverse.flow.oidc.providers;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.oidc.AuthenticationContext;
import io.quarkiverse.flow.oidc.AuthenticationMode;
import io.quarkiverse.flow.oidc.AuthenticationProvider;
import io.quarkiverse.flow.oidc.client.TokenExchangeClient;

/**
 * Service-to-service authentication via a named {@code quarkus-oidc-client}. No user context, the acquired
 * token is cached per scheme so steady-state calls do not hit the token endpoint.
 */
@ApplicationScoped
public class ClientCredentialsProvider implements AuthenticationProvider {

    private static final String SUBJECT_PLACEHOLDER = "client-credentials";

    @Inject
    TokenExchangeClient tokenExchangeClient;

    @Inject
    CachedTokenSource cachedTokenSource;

    @Override
    public boolean supports(AuthenticationMode mode) {
        return mode == AuthenticationMode.CLIENT_CREDENTIALS;
    }

    @Override
    public Optional<String> resolveToken(AuthenticationContext context) {
        return cachedTokenSource.getOrAcquire(context, SUBJECT_PLACEHOLDER, tokenExchangeClient::clientCredentials);
    }
}
