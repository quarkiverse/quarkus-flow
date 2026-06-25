package io.quarkiverse.flow.oidc.providers;

import java.util.Optional;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.oidc.AuthenticationContext;
import io.quarkiverse.flow.oidc.cache.CachedToken;
import io.quarkiverse.flow.oidc.cache.TokenCacheKey;
import io.quarkiverse.flow.oidc.cache.TokenCacheRepository;
import io.quarkiverse.flow.oidc.client.OidcClientProvider;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;

/**
 * Shared cache-or-acquire flow for providers that obtain a token from a named {@code quarkus-oidc-client}:
 * return the cached token when present, otherwise resolve the client, acquire a fresh token, cache it (linked
 * to the workflow instance) and return its access token.
 */
@ApplicationScoped
public class CachedTokenSource {

    @Inject
    OidcClientProvider oidcClientProvider;

    @Inject
    TokenCacheRepository cache;

    /**
     * @param context the current call context (carries the scheme name, its config and the instance id)
     * @param subjectForKey the value that, with the scheme, identifies the cache entry — the subject token
     *        for exchange, or a fixed placeholder for client-credentials
     * @param acquire how to obtain fresh tokens from the resolved {@link OidcClient} on a cache miss
     * @return the access token to attach, or empty when none could be produced
     */
    public Optional<String> getOrAcquire(AuthenticationContext context, String subjectForKey,
            Function<OidcClient, Tokens> acquire) {
        TokenCacheKey key = TokenCacheKey.from(context.schemeName(), subjectForKey, "");

        Optional<CachedToken> cached = cache.get(key);
        if (cached.isPresent()) {
            return Optional.of(cached.get().token());
        }

        OidcClient client = oidcClientProvider.resolve(context.schemeName(),
                context.schemeConfig().oidcClientName());
        Tokens tokens = acquire.apply(client);
        cache.store(ProviderTokens.toCachedToken(key, tokens), context.instanceId());
        return Optional.ofNullable(tokens.getAccessToken());
    }
}