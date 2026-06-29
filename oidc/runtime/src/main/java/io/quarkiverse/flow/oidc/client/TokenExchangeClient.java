package io.quarkiverse.flow.oidc.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;

/**
 * Thin wrapper over {@link OidcClient} for client-credentials and
 * RFC 8693 token exchange.
 */
@ApplicationScoped
public class TokenExchangeClient {

    public static final String TOKEN_TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";

    /**
     * Client-credentials acquisition, no user context. Blocks until the token is materialized.
     */
    public Tokens clientCredentials(OidcClient client) {
        return client.getTokens().await().indefinitely();
    }

    /**
     * RFC 8693 token exchange. The configured {@code quarkus.oidc-client.<name>.grant.type} drives the
     * {@code grant_type}; subject token, audience and scopes are passed as additional grant parameters.
     * Blocks until the token is materialized.
     */
    public Tokens exchange(OidcClient client, String subjectToken, Optional<String> audience,
            Optional<List<String>> scopes) {
        Map<String, String> params = new HashMap<>();
        params.put("subject_token", subjectToken);
        params.put("subject_token_type", TOKEN_TYPE_ACCESS_TOKEN);
        audience.filter(a -> !a.isBlank()).ifPresent(a -> params.put("audience", a));
        scopes.filter(s -> !s.isEmpty()).ifPresent(s -> params.put("scope", String.join(" ", s)));
        return client.getTokens(params).await().indefinitely();
    }
}
