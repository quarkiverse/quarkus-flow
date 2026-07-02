package io.quarkiverse.flow.oidc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.oidc.client.runtime.OidcClientConfig;

/**
 * Builds and caches {@link OidcClient} instances created on the fly from workflow OAuth2/OIDC policies.
 *
 * <p>
 * Clients are keyed by {@link CacheKey}, which covers every value baked into the {@link OidcClientConfig} (authority, token
 * endpoint, OAuth2-vs-OIDC flag, client id, client-authentication method, grant, scopes, audiences and the credential
 * material). Two call sites reuse a single {@link OidcClient} — and therefore its token cache and refresh logic — only when
 * all of those match.
 */
@ApplicationScoped
public class OidcClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(OidcClientFactory.class);

    final OidcClients oidcClients;
    final Map<CacheKey, OidcClient> cache = new ConcurrentHashMap<>();

    @Inject
    public OidcClientFactory(OidcClients oidcClients) {
        this.oidcClients = oidcClients;
    }

    public OidcClient get(CacheKey key, Supplier<OidcClientConfig> configSupplier) {
        return cache.computeIfAbsent(key, k -> oidcClients.newClient(configSupplier.get()).await().indefinitely());
    }

    @PreDestroy
    public void safeClose() {
        cache.values().forEach(OidcClientFactory::safeClose);
        cache.clear();
    }

    private static void safeClose(OidcClient client) {
        try {
            client.close();
        } catch (Exception e) {
            LOG.debug("Flow OIDC: failed to close an OidcClient", e);
        }
    }
}
