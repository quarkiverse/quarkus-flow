package io.quarkiverse.flow.oidc.registry;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;

@ApplicationScoped
@Unremovable
public class OidcClientRegistry {

    private final Map<String, OidcClient> runtimeOidcClients = new ConcurrentHashMap<>();
    private final Map<EndpointKey, String> endpointToName = new ConcurrentHashMap<>();

    @Inject
    OidcClients clients;

    /**
     * Register a runtime OIDC client with both name-based and endpoint-based indexing.
     *
     * @param clientName the client name (e.g., namespace:name:version.task.taskName)
     * @param client the OIDC client instance
     * @param endpointKey the endpoint key (used to build endpoint key for matching)
     */
    public void register(String clientName, OidcClient client, EndpointKey endpointKey) {
        runtimeOidcClients.put(clientName, client);
        endpointToName.put(endpointKey, clientName);
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
        String clientName = endpointToName.get(key);
        return clientName != null ? get(clientName) : null;
    }

    @PreDestroy
    void closeClients() {
        for (OidcClient client : runtimeOidcClients.values()) {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to close OidcClient.", e);
                }
            }
        }
    }
}
