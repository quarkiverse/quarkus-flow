package io.quarkiverse.flow.oidc;

/**
 * How a workflow task authenticates against a downstream service.
 */
public enum AuthenticationMode {

    /**
     * Forward the caller's subject token unchanged (no exchange, no OIDC call).
     */
    TOKEN_PROPAGATION,

    /**
     * Swap the caller's subject token for a service-specific token via RFC 8693 token exchange,
     * using a named {@code quarkus-oidc-client}.
     */
    TOKEN_EXCHANGE,

    /**
     * Service-to-service authentication using a named {@code quarkus-oidc-client}; no user context.
     */
    CLIENT_CREDENTIALS
}
