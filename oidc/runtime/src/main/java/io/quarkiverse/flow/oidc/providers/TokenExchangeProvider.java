package io.quarkiverse.flow.oidc.providers;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.oidc.AuthenticationContext;
import io.quarkiverse.flow.oidc.AuthenticationMode;
import io.quarkiverse.flow.oidc.AuthenticationProvider;
import io.quarkiverse.flow.oidc.client.TokenExchangeClient;

/**
 * Swaps the caller's subject token for a service-specific token via RFC 8693, using a named
 * {@code quarkus-oidc-client}. Exchanged tokens are cached per (scheme, subject-token-hash, audience) and
 * linked to the workflow instance so they are evicted when the instance terminates.
 */
@ApplicationScoped
public class TokenExchangeProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(TokenExchangeProvider.class);

    @Inject
    TokenExchangeClient tokenExchangeClient;

    @Inject
    CachedTokenSource cachedTokenSource;

    @Override
    public boolean supports(AuthenticationMode mode) {
        return mode == AuthenticationMode.TOKEN_EXCHANGE;
    }

    @Override
    public Optional<String> resolveToken(AuthenticationContext context) {
        log.info("Running ClientCredentialsProvider");
        Optional<String> subjectToken = context.subjectToken();
        if (subjectToken.isEmpty()) {
            log.warn("Flow OIDC: no subject token for exchange scheme '{}'; request sent without Authorization.",
                    context.schemeName());
            return Optional.empty();
        }

        String token = subjectToken.get();
        return cachedTokenSource.getOrAcquire(context, token,
                client -> tokenExchangeClient.exchange(client, token, Optional.empty(), Optional.empty()));
    }
}
