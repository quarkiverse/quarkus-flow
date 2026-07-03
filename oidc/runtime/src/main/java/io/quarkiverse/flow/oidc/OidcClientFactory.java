package io.quarkiverse.flow.oidc;

import java.time.Duration;
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
import io.quarkus.oidc.client.runtime.OidcClientsConfig;

/**
 * Builds and caches {@link OidcClient} instances created on the fly from workflow OAuth2/OIDC policies.
 *
 * <p>
 * Clients are keyed by {@link CacheKey}, which covers every value baked into the {@link OidcClientConfig} (authority, token
 * endpoint, OAuth2-vs-OIDC flag, client id, client-authentication method, grant, scopes, audiences and the credential
 * material). Two call sites reuse a single {@link OidcClient} only when all of those match. Only the client is cached:
 * {@link OidcClient#getTokens()} performs a fresh token grant request on every invocation, so a new token is negotiated
 * for each authenticated call.
 */
@ApplicationScoped
public class OidcClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(OidcClientFactory.class);
    final OidcClients oidcClients;
    final Map<CacheKey, OidcClient> cache = new ConcurrentHashMap<>();
    private final Duration globalCreationTimeout;
    private final Duration globalConnectionTimeout;
    private final OidcClientsConfig oidcClientsConfig;

    @Inject
    public OidcClientFactory(OidcClients oidcClients, FlowOidcConfig config, OidcClientsConfig oidcClientsConfig) {
        this.oidcClients = oidcClients;
        this.globalCreationTimeout = config.creationTimeout();
        this.globalConnectionTimeout = config.connectionTimeout();
        this.oidcClientsConfig = oidcClientsConfig;
    }

    public OidcClient get(CacheKey key, Supplier<OidcClientConfig> configSupplier, Duration creationTimeout) {
        return cache.computeIfAbsent(key, k -> {
            try {
                return oidcClients.newClient(configSupplier.get()).await().atMost(creationTimeout);
            } catch (Exception e) {
                throw new IllegalStateException("Flow OIDC: failed to create OIDC client for key: " + k, e);
            }
        });
    }

    public OidcClient getNamedClient(String name) {
        return oidcClients.getClient(name);
    }

    /**
     * The global timeout that bounds Flow's blocking {@code await} while an {@link OidcClient} instance is created on the
     * fly from a workflow DSL policy.
     */
    public Duration clientCreationTimeout() {
        return globalCreationTimeout;
    }

    /**
     * The global {@code connection-timeout} applied to clients Flow builds on the fly from a workflow DSL policy, and the
     * fallback await ceiling for named clients that declare no {@code quarkus.oidc-client.<name>.connection-timeout}.
     */
    public Duration connectionTimeout() {
        return globalConnectionTimeout;
    }

    public Duration namedConnectionTimeout(String clientName) {
        final OidcClientConfig named = oidcClientsConfig.namedClients().get(clientName);
        return named != null ? named.connectionTimeout() : globalConnectionTimeout;
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
