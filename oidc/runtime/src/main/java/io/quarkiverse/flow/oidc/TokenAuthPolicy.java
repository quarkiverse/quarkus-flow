package io.quarkiverse.flow.oidc;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import io.quarkiverse.flow.oidc.registry.EndpointKey;
import io.quarkiverse.flow.oidc.registry.OidcClientRegistry;
import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.EndpointConfiguration;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;

/**
 * Token-based authentication policy (OAuth2 or OIDC) extracted from a workflow.
 * <p>
 * Either {@link #oidc()} or {@link #oauth2()} will be present, never both.
 * OIDC uses discovery; OAuth2 requires explicit endpoint configuration.
 */
public class TokenAuthPolicy implements Serializable {

    private final OAuth2AuthenticationData oidc;
    private final OAuth2ConnectAuthenticationProperties oauth2;
    private final String name;
    private final String taskName;

    public TokenAuthPolicy(String name, OAuth2AuthenticationData oidc) {
        this(name, null, oidc);
    }

    public TokenAuthPolicy(String name, String taskName, OAuth2AuthenticationData oidc) {
        Objects.requireNonNull(oidc, "oidc is required");
        this.oidc = oidc;
        this.oauth2 = null;
        this.name = name;
        this.taskName = taskName;
    }

    public TokenAuthPolicy(String name, OAuth2ConnectAuthenticationProperties oauth2) {
        this(name, null, oauth2);
    }

    public TokenAuthPolicy(String name, String taskName, OAuth2ConnectAuthenticationProperties oauth2) {
        Objects.requireNonNull(oauth2, "oauth2AuthenticationData is required");
        this.oidc = null;
        this.oauth2 = oauth2;
        this.name = name;
        this.taskName = taskName;
    }

    /**
     * Extracts token auth policy from an authentication policy union.
     * <p>
     * <b>Note:</b> This method directly accesses SDK properties rather than delegating to
     * {@link #tokenAuthData(AuthenticationPolicyUnion)} because the SDK sometimes boxes/shadows
     * OIDC endpoints as OAuth2 properties in certain scenarios, causing type detection failures.
     * The duplication is intentional to ensure correct type preservation for {@link TokenAuthPolicy}
     * construction.
     *
     * @return policy if OAuth2 or OIDC, empty otherwise
     */
    public static Optional<TokenAuthPolicy> from(String name, AuthenticationPolicyUnion policyUnion) {
        if (policyUnion == null) {
            return Optional.empty();
        }
        if (policyUnion.getOAuth2AuthenticationPolicy() != null)
            return Optional.of(new TokenAuthPolicy(name,
                    policyUnion.getOAuth2AuthenticationPolicy().getOauth2().getOAuth2ConnectAuthenticationProperties()));

        if (policyUnion.getOpenIdConnectAuthenticationPolicy() != null) {
            return Optional.of(new TokenAuthPolicy(name,
                    policyUnion.getOpenIdConnectAuthenticationPolicy().getOidc().getOpenIdConnectAuthenticationProperties()));
        }
        return Optional.empty();
    }

    public static OAuth2AuthenticationData tokenAuthData(AuthenticationPolicyUnion policyUnion) {
        if (policyUnion == null) {
            return null;
        }
        if (policyUnion.getOAuth2AuthenticationPolicy() != null) {
            var oauth2Config = policyUnion.getOAuth2AuthenticationPolicy().getOauth2();
            return oauth2Config != null ? oauth2Config.getOAuth2ConnectAuthenticationProperties() : null;
        }
        if (policyUnion.getOpenIdConnectAuthenticationPolicy() != null) {
            var oidcConfig = policyUnion.getOpenIdConnectAuthenticationPolicy().getOidc();
            return oidcConfig != null ? oidcConfig.getOpenIdConnectAuthenticationProperties() : null;
        }
        return null;
    }

    /**
     * Extracts token auth policy from endpoint configuration.
     *
     * @return policy if authentication is OAuth2 or OIDC, empty otherwise
     */
    public static Optional<TokenAuthPolicy> from(String name, EndpointConfiguration endpointConfiguration) {
        Objects.requireNonNull(endpointConfiguration, "endpointConfiguration is required");
        if (endpointConfiguration.getAuthentication() != null) {
            return from(name, endpointConfiguration.getAuthentication().getAuthenticationPolicy());
        }
        return Optional.empty();
    }

    public String name() {
        return name;
    }

    public String namePropertySafe() {
        return String.format("\"%s\"", name());
    }

    /**
     * @return task name for inline authentication, empty for named policies
     */
    public Optional<String> taskName() {
        return Optional.ofNullable(taskName);
    }

    /**
     * @return OIDC authentication data (uses discovery, no endpoints)
     */
    public Optional<OAuth2AuthenticationData> oidc() {
        return Optional.ofNullable(oidc);
    }

    /**
     * @return OAuth2 authentication data (has explicit endpoints)
     */
    public Optional<OAuth2ConnectAuthenticationProperties> oauth2() {
        return Optional.ofNullable(oauth2);
    }

    /**
     * @return the common configuration for this policy. If specific OAuth2 data required (it has endpoints definition), use
     *         {@link #oauth2()}
     */
    public OAuth2AuthenticationData commonAuth() {
        if (oauth2 != null)
            return oauth2;
        return oidc;
    }

    /**
     * Computes an endpoint key for matching this policy to registered OIDC clients.
     * <p>
     * The key includes ALL configuration that makes a client unique: authority, endpoints,
     * credentials (clientId, clientSecret, username, password), grant type, scopes, and audiences.
     * <p>
     * Used by {@link OidcClientRegistry} to find clients by configuration instead of by name.
     *
     * @return endpoint key for client lookup
     */
    public EndpointKey endpointKey() {
        return EndpointKey.from(commonAuth());
    }

}
