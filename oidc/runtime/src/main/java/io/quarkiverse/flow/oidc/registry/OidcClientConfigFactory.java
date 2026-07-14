package io.quarkiverse.flow.oidc.registry;

import java.net.URI;
import java.time.Duration;

import io.quarkiverse.flow.oidc.TokenAuthPolicy;
import io.quarkus.oidc.client.OidcClientConfigBuilder;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.common.runtime.config.OidcClientCommonConfig;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;

final class OidcClientConfigFactory {

    /**
     * Default OAuth2 token endpoint path per CNCF Serverless Workflow specification.
     * Used when endpoints.token is not explicitly specified in OAuth2 authentication.
     */
    private static final String DEFAULT_TOKEN_PATH = "/oauth2/token";

    /**
     * Maps Open Workflow Specification grant types to Quarkus OIDC Grant.Type enum.
     * Used for OidcClientConfigBuilder at runtime.
     * Handles both enum constant names (uppercase) and JSON values (lowercase with underscores).
     */
    private static OidcClientConfig.Grant.Type mapGrantTypeToEnum(String swfGrant) {
        return switch (swfGrant.toUpperCase()) {
            case "CLIENT_CREDENTIALS" -> OidcClientConfig.Grant.Type.CLIENT;
            case "PASSWORD" -> OidcClientConfig.Grant.Type.PASSWORD;
            case "AUTHORIZATION_CODE" -> OidcClientConfig.Grant.Type.CODE;
            case "REFRESH_TOKEN" -> OidcClientConfig.Grant.Type.REFRESH;
            case "URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE", "URN:IETF:PARAMS:OAUTH:GRANT-TYPE:TOKEN-EXCHANGE" ->
                OidcClientConfig.Grant.Type.EXCHANGE;
            default ->
                throw new IllegalArgumentException("Unsupported grant type for runtime client creation: " + swfGrant);
        };
    }

    private static OidcClientConfig.Grant.Type mapGrantTypeToEnum(
            OAuth2AuthenticationData.OAuth2AuthenticationDataGrant swfGrant) {
        return mapGrantTypeToEnum(swfGrant.value());
    }

    /**
     * Maps the policy's client authentication scheme to the way Quarkus sends the client credentials. The Serverless
     * Workflow default is {@code client_secret_post} (credentials in the request body), so we default to {@code POST}
     * rather than Quarkus' own {@code client_secret_basic} default. {@code PRIVATE_KEY_JWT} relies on an asymmetric signing
     * key (not a shared client secret) and cannot be honoured from a policy that only carries a {@code secret}, so it is
     * rejected explicitly instead of being silently downgraded to a {@code client_secret_jwt} (HMAC) assertion.
     */
    private static OidcClientCommonConfig.Credentials.Secret.Method clientSecretMethod(OAuth2AuthenticationDataClient client) {
        if (client == null || client.getAuthentication() == null) {
            return OidcClientCommonConfig.Credentials.Secret.Method.POST;
        }
        return clientSecretMethod(client.getAuthentication());
    }

    private static OidcClientCommonConfig.Credentials.Secret.Method clientSecretMethod(
            OAuth2AuthenticationDataClient.ClientAuthentication clientAuth) {
        return switch (clientAuth) {
            case CLIENT_SECRET_BASIC -> OidcClientCommonConfig.Credentials.Secret.Method.BASIC;
            case CLIENT_SECRET_JWT -> OidcClientCommonConfig.Credentials.Secret.Method.POST_JWT;
            case PRIVATE_KEY_JWT -> throw new IllegalStateException(
                    "Flow OIDC: PRIVATE_KEY_JWT client authentication is not supported; use CLIENT_SECRET_BASIC, "
                            + "CLIENT_SECRET_POST or CLIENT_SECRET_JWT");
            default -> OidcClientCommonConfig.Credentials.Secret.Method.POST;
        };
    }

    private static void validateAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            throw new IllegalStateException("OAuth2/OIDC authority cannot be null or blank");
        }
        try {
            URI uri = URI.create(authority);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                throw new IllegalStateException("OAuth2/OIDC authority must use http or https scheme: " + authority);
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("OAuth2/OIDC authority must have a valid host: " + authority);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("OAuth2/OIDC authority is not a valid URI: " + authority, e);
        }
    }

    public static OidcClientConfig from(TokenAuthPolicy policy, Duration connectionTimeout) {
        final OAuth2AuthenticationData authData = policy.commonAuth();
        final boolean isOidc = policy.oidc().isPresent();

        validateAuthority(authData.getAuthority().getLiteralUri().toString());

        final OidcClientConfigBuilder builder = new OidcClientConfigBuilder()
                .id(policy.name())
                .authServerUrl(authData.getAuthority().getLiteralUri().toString())
                .discoveryEnabled(isOidc)
                .connectionTimeout(connectionTimeout);

        if (!isOidc) {
            final var oauthEndpoints = ((OAuth2ConnectAuthenticationProperties) authData).getEndpoints();
            String tokenPath = DEFAULT_TOKEN_PATH; // Spec default

            if (oauthEndpoints != null && oauthEndpoints.getToken() != null) {
                tokenPath = oauthEndpoints.getToken();
            }

            builder.tokenPath(tokenPath);
        }

        final String clientId = authData.getClient() != null ? authData.getClient().getId() : policy.name();
        builder.clientId(clientId);

        if (authData.getClient() != null && authData.getClient().getSecret() != null) {
            builder.credentials().clientSecret(authData.getClient().getSecret(), clientSecretMethod(authData.getClient()))
                    .end();
        }

        if (authData.getGrant() != null) {
            builder.grant(mapGrantTypeToEnum(authData.getGrant().name()));
        }

        if (authData.getScopes() != null && !authData.getScopes().isEmpty()) {
            builder.scopes(authData.getScopes());
        }

        if (authData.getAudiences() != null && !authData.getAudiences().isEmpty()) {
            builder.audience(authData.getAudiences());
        }

        return builder.build();
    }

    public static OidcClientConfig from(EndpointKey endpointKey, Duration connectionTimeout) {
        validateAuthority(endpointKey.authority());

        OidcClientConfigBuilder builder = new OidcClientConfigBuilder()
                .id(endpointKey.oidcId())
                .authServerUrl(endpointKey.authority())
                .discoveryEnabled(endpointKey.isDiscoverable())
                .connectionTimeout(connectionTimeout)
                .clientId(endpointKey.clientId())
                .credentials().clientSecret(endpointKey.clientSecret(), clientSecretMethod(endpointKey.clientAuthMethod()))
                .end()
                .grant(mapGrantTypeToEnum(endpointKey.grant()))
                .scopes(endpointKey.scopes())
                .audience(endpointKey.audiences());

        // Only set tokenPath for OAuth2 (not OIDC with discovery)
        if (!endpointKey.isDiscoverable() && endpointKey.tokenPath() != null) {
            builder.tokenPath(endpointKey.tokenPath());
        }

        return builder.build();
    }

}
