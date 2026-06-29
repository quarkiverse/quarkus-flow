package io.quarkiverse.flow.oidc.providers;

import java.time.Instant;
import java.util.Set;

import io.quarkiverse.flow.oidc.cache.CachedToken;
import io.quarkiverse.flow.oidc.cache.TokenCacheKey;
import io.quarkus.oidc.client.Tokens;

/**
 * Helpers shared by the OIDC providers.
 */
final class ProviderTokens {

    /**
     * Fallback TTL when the token endpoint does not return an expiry, so a token is still briefly cached.
     */
    private static final long DEFAULT_TTL_SECONDS = 300L;

    private ProviderTokens() {
    }

    static CachedToken toCachedToken(TokenCacheKey key, Tokens tokens) {
        Instant expiresAt = tokens.getAccessTokenExpiresAt() != null
                ? Instant.ofEpochSecond(tokens.getAccessTokenExpiresAt())
                : Instant.now().plusSeconds(DEFAULT_TTL_SECONDS);
        return new CachedToken(key, tokens.getAccessToken(), expiresAt, Instant.now(), Set.of());
    }
}
