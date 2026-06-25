package io.quarkiverse.flow.oidc.client;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;

/**
 * Resolves named {@code quarkus-oidc-client} instances for an auth scheme.
 */
@ApplicationScoped
public class OidcClientProvider {

    @Inject
    OidcClients oidcClients;

    /**
     * Resolve the OIDC client for a scheme. Precedence: explicit {@code oidc-client-name} on the scheme,
     * otherwise the scheme name itself, otherwise the default client.
     */
    public OidcClient resolve(String schemeName, Optional<String> oidcClientName) {
        String clientName = oidcClientName.filter(s -> !s.isBlank()).orElse(schemeName);
        if (clientName == null || clientName.isBlank()) {
            return oidcClients.getClient();
        }
        OidcClient client = oidcClients.getClient(clientName);
        if (client == null) {
            throw new IllegalStateException(
                    "OIDC client not configured: " + clientName
                            + ". Configure quarkus.oidc-client." + clientName + ".*");
        }
        return client;
    }
}
