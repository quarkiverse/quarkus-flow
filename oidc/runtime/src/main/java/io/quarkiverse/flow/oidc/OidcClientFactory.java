package io.quarkiverse.flow.oidc;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.oidc.client.runtime.OidcClientConfig;

/**
 * Builds and caches {@link OidcClient} instances created on the fly from workflow OAuth2/OIDC policies.
 *
 * <p>
 * Clients are keyed by the stable identity computed in
 * {@link OidcClientAuthProvider}{@code #cacheKey(...)}, which covers every value baked into the
 * {@link OidcClientConfig} (authority, token endpoint, OAuth2-vs-OIDC flag, client id, client-authentication method, grant,
 * scopes, audiences and the credential material). Two call sites reuse a single {@link OidcClient} — and therefore its token
 * cache and refresh logic — only when all of those match.
 */
public final class OidcClientFactory implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OidcClientFactory.class);

    private final OidcClients oidcClients;
    private final Map<String, OidcClient> cache = new ConcurrentHashMap<>();

    public OidcClientFactory(OidcClients oidcClients) {
        this.oidcClients = oidcClients;
    }

    public OidcClient get(String key, Supplier<OidcClientConfig> configSupplier, Duration buildTimeout) {
        return cache.computeIfAbsent(key, k -> oidcClients.newClient(configSupplier.get()).await().atMost(buildTimeout));
    }

    @Override
    public void close() {
        cache.values().forEach(OidcClientFactory::close);
        cache.clear();
    }

    private static void close(OidcClient client) {
        try {
            client.close();
        } catch (Exception e) {
            LOG.debug("Flow OIDC: failed to close an OidcClient", e);
        }
    }
}
