package io.quarkiverse.flow.oidc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Overridable properties for an OIDC client created by Quarkus Flow.
 *
 * <p>
 * Each property is {@link Optional} so that only explicitly set values override lower-specificity layers. When used as
 * global defaults (the root of {@link FlowOidcConfig}), unset properties fall through to the DSL-declared values. When
 * used in a per-workflow/task override, unset properties fall through to the next less-specific layer.
 */
public interface OidcClientOverrideConfig {

    /**
     * The base URL of the OpenID Connect (OIDC) server. Overrides the DSL {@code authority}.
     */
    Optional<String> authServerUrl();

    /**
     * Relative path or absolute URL of the OIDC token endpoint.
     */
    Optional<String> tokenPath();

    /**
     * Whether OIDC discovery should be used to locate endpoints.
     */
    Optional<Boolean> discoveryEnabled();

    /**
     * The client ID used for authentication. Overrides the DSL {@code clientId}.
     */
    Optional<String> clientId();

    /**
     * The client secret used for authentication. Overrides the DSL {@code clientSecret}.
     */
    Optional<String> clientSecret();

    /**
     * Method used to send the client secret: {@code BASIC}, {@code POST}, {@code POST_JWT}.
     */
    Optional<String> clientSecretMethod();

    /**
     * OAuth2 grant type. Supported values are {@code authorization_code}, {@code client_credentials},
     * {@code password}, {@code refresh_token} and {@code urn:ietf:params:oauth:grant-type:token-exchange}.
     */
    Optional<String> grantType();

    /**
     * OAuth2 scopes to request. Overrides the DSL scopes.
     */
    Optional<List<String>> scopes();

    /**
     * Target audiences for the token request. Overrides the DSL audiences.
     */
    Optional<List<String>> audience();

    /**
     * Duration after which the access token is considered expired, when the server response does not include an
     * {@code expires_in} value.
     */
    Optional<Duration> accessTokenExpiresIn();

    /**
     * Safety margin subtracted from the access token expiry time to allow for clock skew.
     */
    Optional<Duration> accessTokenExpirySkew();

    /**
     * Duration after which the refresh token is considered expired.
     */
    Optional<Duration> refreshTokenTimeSkew();

    /**
     * If {@code true}, the {@code expires_in} value from the token response is treated as an absolute timestamp rather
     * than a duration.
     */
    Optional<Boolean> absoluteExpiresIn();

    /**
     * If {@code true}, the token is acquired eagerly at client creation time rather than on first use.
     */
    Optional<Boolean> earlyTokensAcquisition();

    /**
     * Custom HTTP headers to include in the token request.
     */
    Map<String, String> headers();

    /**
     * Interval at which the token should be refreshed asynchronously in the background.
     */
    Optional<Duration> refreshInterval();
}