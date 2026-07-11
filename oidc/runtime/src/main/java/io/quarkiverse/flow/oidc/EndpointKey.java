package io.quarkiverse.flow.oidc;

import java.util.List;
import java.util.Objects;

import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;
import io.serverlessworkflow.api.types.OAuth2AuthenticationPropertiesEndpoints;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;

/**
 * Identifies an OIDC client by its complete endpoint configuration.
 * <p>
 * Two endpoint configurations produce the same OIDC client only when ALL fields match.
 * This includes authority, endpoints, credentials, grant type, scopes, and audiences.
 * <p>
 * Used as a Map key in {@link OidcClientRegistry} to match SDK authentication policies
 * to registered OIDC clients.
 * <p>
 * <b>Security Notice:</b> This record contains sensitive credential data (clientSecret, username, password)
 * in plaintext. While {@link #toString()} masks these fields, the actual values are stored in memory
 * and visible via reflection/debugging tools. Do not serialize, log at non-debug levels, or persist
 * instances of this class.
 */
public record EndpointKey(
        String authority,
        String tokenPath,
        String revocationPath,
        boolean openIdConnect,
        String clientId,
        String clientSecret,
        OAuth2AuthenticationDataGrant grant,
        List<String> scopes,
        List<String> audiences,
        String username,
        String password) {

    /**
     * Default OAuth2 token endpoint path per CNCF Serverless Workflow specification.
     * Applied when endpoints.token is not explicitly specified.
     */
    private static final String DEFAULT_TOKEN_PATH = "/oauth2/token";

    /**
     * Default OAuth2 revocation endpoint path per CNCF Serverless Workflow specification.
     * Applied when endpoints.revocation is not explicitly specified.
     */
    private static final String DEFAULT_REVOCATION_PATH = "/oauth2/revoke";

    public EndpointKey {
        // Normalize null collections to empty for consistent equality.
        // This treats "no scopes requested" (null) as equivalent to "explicitly empty scopes" ([]),
        // which is semantically correct for OAuth2/OIDC client matching - both mean the client
        // requests no additional scopes beyond the default.
        scopes = scopes != null ? List.copyOf(scopes) : List.of();
        audiences = audiences != null ? List.copyOf(audiences) : List.of();
    }

    public static EndpointKey from(AuthenticationPolicyUnion policyUnion) {
        return from(TokenAuthPolicy.tokenAuthData(policyUnion));
    }

    public static EndpointKey from(OAuth2AuthenticationData data) {
        if (data == null) {
            return null;
        }
        // Authority
        String authority = data.getAuthority() != null && data.getAuthority().getLiteralUri() != null
                ? data.getAuthority().getLiteralUri().toString()
                : null;

        // OIDC vs OAuth2
        boolean isOidc = !(data instanceof OAuth2ConnectAuthenticationProperties);

        // Endpoints (OAuth2 only, OIDC uses discovery)
        String tokenPath = null;
        String revocationPath = null;

        if (!isOidc) {
            // OAuth2 - apply spec defaults for endpoints
            tokenPath = DEFAULT_TOKEN_PATH;
            revocationPath = DEFAULT_REVOCATION_PATH;

            // Override with explicit values if provided
            OAuth2AuthenticationPropertiesEndpoints endpoints = ((OAuth2ConnectAuthenticationProperties) data)
                    .getEndpoints();
            if (endpoints != null) {
                if (endpoints.getToken() != null) {
                    tokenPath = endpoints.getToken();
                }
                if (endpoints.getRevocation() != null) {
                    revocationPath = endpoints.getRevocation();
                }
            }
        }

        // Client credentials
        String clientId = data.getClient() != null ? data.getClient().getId() : null;
        String clientSecret = data.getClient() != null ? data.getClient().getSecret() : null;

        // Grant type
        OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant = data.getGrant();

        // Scopes and audiences
        List<String> scopes = data.getScopes();
        List<String> audiences = data.getAudiences();

        // Username/password (for password grant)
        String username = data.getUsername();
        String password = data.getPassword();

        return new EndpointKey(
                authority,
                tokenPath,
                revocationPath,
                isOidc,
                clientId,
                clientSecret,
                grant,
                scopes,
                audiences,
                username,
                password);
    }

    @Override
    public String toString() {
        return "EndpointKey{" +
                "authority='" + authority + '\'' +
                ", tokenPath='" + tokenPath + '\'' +
                ", revocationPath='" + revocationPath + '\'' +
                ", openIdConnect=" + openIdConnect +
                ", clientId='" + clientId + '\'' +
                ", grant=" + grant +
                ", scopes=" + scopes +
                ", audiences=" + audiences +
                ", username='" + (username != null ? "***" : null) + '\'' +
                ", password='" + (password != null ? "***" : null) + '\'' +
                ", clientSecret='" + (clientSecret != null ? "***" : null) + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EndpointKey that = (EndpointKey) o;
        return openIdConnect == that.openIdConnect &&
                Objects.equals(authority, that.authority) &&
                Objects.equals(tokenPath, that.tokenPath) &&
                Objects.equals(revocationPath, that.revocationPath) &&
                Objects.equals(clientId, that.clientId) &&
                Objects.equals(clientSecret, that.clientSecret) &&
                grant == that.grant &&
                Objects.equals(scopes, that.scopes) &&
                Objects.equals(audiences, that.audiences) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authority, tokenPath, revocationPath, openIdConnect, clientId,
                clientSecret, grant, scopes, audiences, username, password);
    }
}
