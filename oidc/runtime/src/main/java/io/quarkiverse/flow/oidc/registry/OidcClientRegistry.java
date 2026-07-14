package io.quarkiverse.flow.oidc.registry;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;

/**
 * Registry for OIDC clients created by Quarkus Flow.
 * <p>
 * Provides dual-lookup capability:
 * <ul>
 * <li><b>By name</b> - For named policies, routing overrides, and user-configured clients</li>
 * <li><b>By endpoint configuration</b> - For matching identical OAuth2/OIDC configs via {@link EndpointKey}</li>
 * </ul>
 * <p>
 * The same {@link OidcClient} instance may be indexed multiple ways (by name and by endpoint),
 * allowing efficient lookups from different code paths while avoiding client duplication.
 * <p>
 * Thread-safe via {@link ConcurrentHashMap}.
 */
@ApplicationScoped
@Unremovable
public class OidcClientRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcClientRegistry.class);

    /**
     * Index of OIDC clients by name. Used for:
     * <ul>
     * <li>Named authentication policies (e.g., {@code use("keycloak")})</li>
     * <li>Workflow/task routing overrides (e.g., {@code quarkus.flow.oidc.client.my-workflow.name})</li>
     * <li>User-configured Quarkus OIDC clients (e.g., {@code quarkus.oidc-client.my-client})</li>
     * </ul>
     */
    private final Map<String, OidcClient> runtimeOidcClients = new ConcurrentHashMap<>();

    /**
     * Index of OIDC clients by endpoint configuration. Used for:
     * <ul>
     * <li>Client reuse when two policies have identical OAuth2/OIDC configuration</li>
     * <li>Dynamic client lookups (policies with runtime expressions)</li>
     * </ul>
     * The {@link EndpointKey} includes all configuration fields (authority, credentials, grant, scopes, etc.),
     * so two configurations share a client only when ALL fields match.
     */
    private final Map<EndpointKey, OidcClient> endpointToOidcClients = new ConcurrentHashMap<>();

    @Inject
    OidcClients clients;

    /**
     * Register an OIDC client with both name-based and endpoint-based indexing.
     * <p>
     * The same client instance is indexed in both maps, allowing lookups by name
     * (for routing overrides) or by endpoint configuration (for client reuse).
     *
     * @param clientName the client name (e.g., {@code keycloak}, {@code orders}, or
     *        {@code namespace:name:version.task.taskName}). If empty, the client
     *        is only registered by endpoint.
     * @param client the OIDC client instance
     * @param endpointKey the endpoint key representing the full OAuth2/OIDC configuration.
     *        Used for matching identical configurations across different policies.
     */
    public void register(String clientName, OidcClient client, EndpointKey endpointKey) {
        register(client, endpointKey);
        if (client != null && !clientName.isBlank())
            runtimeOidcClients.put(clientName, client);

        LOGGER.debug("Registered OIDC client with name '{}' and endpoint key.", clientName);
    }

    /**
     * Register an OIDC client by endpoint configuration only (no name).
     * <p>
     * Used for dynamic clients that are only looked up by their endpoint configuration.
     *
     * @param client the OIDC client instance
     * @param endpointKey the endpoint key representing the full OAuth2/OIDC configuration
     */
    public void register(OidcClient client, EndpointKey endpointKey) {
        endpointToOidcClients.put(endpointKey, client);
        LOGGER.debug("Registered OIDC client with endpoint key (no name): {}", endpointKey);
    }

    /**
     * Get OIDC client by name. Checks Quarkus-configured clients first, then runtime-registered clients.
     *
     * @param clientName the client name
     * @return the OIDC client, or null if not found
     */
    public OidcClient get(String clientName) {
        if (clientName == null)
            return null;

        var client = clients.getClient(clientName);
        if (client == null) {
            return runtimeOidcClients.get(clientName);
        }
        LOGGER.trace("Returning OIDC client {}.", clientName);
        return client;
    }

    /**
     * Get OIDC client by endpoint configuration.
     * Used by the auth provider factory to match endpoint configurations to registered clients.
     *
     * @param key the endpoint key (includes all configuration: authority, credentials, grant, scopes, etc.)
     * @return the OIDC client, or null if no matching client found
     */
    public OidcClient getByEndpoint(EndpointKey key) {
        if (key == null)
            return null;
        LOGGER.trace("Trying to fetch OIDC client by endpoint key '{}'.", key);
        return endpointToOidcClients.get(key);
    }

    @PreDestroy
    void closeClients() {
        for (OidcClient client : endpointToOidcClients.values()) {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close OidcClient", e);
                }
            }
        }
    }
}
