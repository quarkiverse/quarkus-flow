package io.quarkiverse.flow.oidc;

import java.time.Duration;

import io.quarkus.oidc.client.OidcClientConfigBuilder;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;

public final class OidcClientConfigFactory {

    /**
     * Default OAuth2 token endpoint path per CNCF Serverless Workflow specification.
     * Used when endpoints.token is not explicitly specified in OAuth2 authentication.
     */
    private static final String DEFAULT_TOKEN_PATH = "/oauth2/token";

    /**
     * Maps Open Workflow Specification grant types to Quarkus OIDC Grant.Type enum.
     * Used for OidcClientConfigBuilder at runtime.
     */
    private static OidcClientConfig.Grant.Type mapGrantTypeToEnum(String swfGrant) {
        return switch (swfGrant) {
            case "CLIENT_CREDENTIALS" -> OidcClientConfig.Grant.Type.CLIENT;
            case "PASSWORD" -> OidcClientConfig.Grant.Type.PASSWORD;
            case "AUTHORIZATION_CODE" -> OidcClientConfig.Grant.Type.CODE;
            case "REFRESH_TOKEN" -> OidcClientConfig.Grant.Type.REFRESH;
            case "URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE" -> OidcClientConfig.Grant.Type.EXCHANGE;
            default -> throw new IllegalArgumentException("Unsupported grant type for runtime client creation: " + swfGrant);
        };
    }

    public static OidcClientConfig from(TokenAuthPolicy policy, Duration connectionTimeout) {
        final OAuth2AuthenticationData authData = policy.commonAuth();
        boolean isOidc = policy.oidc().isPresent();
        OidcClientConfigBuilder builder = new OidcClientConfigBuilder()
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
            builder.credentials().clientSecret(authData.getClient().getSecret()).end();
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

}
