package io.quarkiverse.flow.oidc.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient;
import io.serverlessworkflow.api.types.OAuth2AuthenticationPropertiesEndpoints;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;
import io.serverlessworkflow.api.types.UriTemplate;

class EndpointKeyTest {

    @Test
    @DisplayName("equals() - identical configs produce equal keys")
    void equals_identical_configs() {
        // Given: Two identical OAuth2 configs
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                List.of("read"),
                null);

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                List.of("read"),
                null);

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then
        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    @DisplayName("equals() - different clientId produces different keys")
    void equals_different_client_id() {
        // Given: Two configs with different clientId
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2Props(
                "https://auth.example.com",
                "client-A",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2Props(
                "https://auth.example.com",
                "client-B",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("equals() - different clientSecret produces different keys")
    void equals_different_client_secret() {
        // Given: Two configs with different clientSecret
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "secret-A",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "secret-B",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("equals() - different authority produces different keys")
    void equals_different_authority() {
        // Given: Two configs with different authority
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2Props(
                "https://auth1.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2Props(
                "https://auth2.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("equals() - different scopes produces different keys")
    void equals_different_scopes() {
        // Given: Two configs with different scopes
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                List.of("read"),
                null);

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                List.of("read", "write"),
                null);

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("equals() - different grant type produces different keys")
    void equals_different_grant() {
        // Given: Two configs with different grant types
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2PropsWithPassword(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                "user",
                "pass");

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("equals() - null scopes equals empty scopes")
    void equals_null_scopes_equals_empty_scopes() {
        // Given: One config with null scopes, one with empty list
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                List.of(),
                null);

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then: null and empty should be equal (normalization)
        assertThat(key1).isEqualTo(key2);
        assertThat(key1.scopes()).isEmpty();
        assertThat(key2.scopes()).isEmpty();
    }

    @Test
    @DisplayName("equals() - null audiences equals empty audiences")
    void equals_null_audiences_equals_empty_audiences() {
        // Given: One config with null audiences, one with empty list
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                List.of());

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then: null and empty should be equal (normalization)
        assertThat(key1).isEqualTo(key2);
        assertThat(key1.audiences()).isEmpty();
        assertThat(key2.audiences()).isEmpty();
    }

    @Test
    @DisplayName("toString() - masks sensitive fields")
    void toString_masks_sensitive_fields() {
        // Given: EndpointKey with credentials
        OAuth2ConnectAuthenticationProperties props = createOAuth2PropsWithPassword(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                "myuser",
                "mypass");

        EndpointKey key = EndpointKey.from(props);

        // When
        String result = key.toString();

        // Then: Should mask sensitive fields
        assertThat(result).contains("clientSecret='***'");

        // Should NOT contain actual values
        assertThat(result).doesNotContain("my-secret");

        // Should contain non-sensitive fields
        assertThat(result).contains("authority='https://auth.example.com'");
        assertThat(result).contains("clientId='my-client'");
    }

    @Test
    @DisplayName("toString() - shows null for absent clientSecret")
    void toString_shows_null_for_absent_credentials() {
        // Given: EndpointKey without clientSecret
        OAuth2ConnectAuthenticationProperties props = mock(OAuth2ConnectAuthenticationProperties.class);

        UriTemplate authority = mock(UriTemplate.class);
        when(authority.getLiteralUri()).thenReturn(URI.create("https://auth.example.com"));
        when(props.getAuthority()).thenReturn(authority);

        OAuth2AuthenticationDataClient client = mock(OAuth2AuthenticationDataClient.class);
        when(client.getId()).thenReturn("my-client");
        when(client.getSecret()).thenReturn(null);
        when(props.getClient()).thenReturn(client);

        when(props.getGrant()).thenReturn(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
        when(props.getEndpoints()).thenReturn(null);

        EndpointKey key = EndpointKey.from(props);

        // When
        String result = key.toString();

        // Then: Should show null for absent clientSecret
        assertThat(result).contains("clientSecret='null'");
    }

    @Test
    @DisplayName("constructor - null authority throws exception")
    void constructor_null_authority_throws() {
        // When/Then
        assertThatThrownBy(() -> new EndpointKey(
                null,
                "/oauth2/token",
                "/oauth2/revoke",
                false,
                "my-client",
                "my-secret",
                OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_POST,
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Authority is required");
    }

    @Test
    @DisplayName("constructor - blank authority throws exception")
    void constructor_blank_authority_throws() {
        // When/Then
        assertThatThrownBy(() -> new EndpointKey(
                "   ",
                "/oauth2/token",
                "/oauth2/revoke",
                false,
                "my-client",
                "my-secret",
                OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_POST,
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Authority is required");
    }

    @Test
    @DisplayName("defaultOidcId() - uses clientId when available")
    void oidc_id_uses_client_id() {
        // Given
        OAuth2ConnectAuthenticationProperties props = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        EndpointKey key = EndpointKey.from(props);

        // When/Then
        assertThat(key.defaultOidcId()).isEqualTo("flow-oidc-my-client");
    }

    @Test
    @DisplayName("defaultOidcId() - uses authority when clientId is null")
    void oidc_id_uses_authority_when_client_id_null() {
        // Given
        EndpointKey key = new EndpointKey(
                "https://auth.example.com",
                "/oauth2/token",
                "/oauth2/revoke",
                false,
                null, // no clientId
                "my-secret",
                OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_POST,
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        // When/Then
        assertThat(key.defaultOidcId()).isEqualTo("flow-oidc-https://auth.example.com");
    }

    @Test
    @DisplayName("isDiscoverable() - returns true for OIDC")
    void is_discoverable_true_for_oidc() {
        // Given: OpenID Connect (not OAuth2ConnectAuthenticationProperties)
        EndpointKey key = new EndpointKey(
                "https://auth.example.com",
                null, // OIDC uses discovery, no explicit paths
                null,
                true, // openIdConnect = true
                "my-client",
                "my-secret",
                OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_POST,
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        // When/Then
        assertThat(key.isDiscoverable()).isTrue();
    }

    @Test
    @DisplayName("isDiscoverable() - returns false for OAuth2")
    void is_discoverable_false_for_oauth2() {
        // Given: OAuth2 (OAuth2ConnectAuthenticationProperties)
        OAuth2ConnectAuthenticationProperties props = createOAuth2Props(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                null,
                null);

        EndpointKey key = EndpointKey.from(props);

        // When/Then
        assertThat(key.isDiscoverable()).isFalse();
    }

    @Test
    @DisplayName("from() - applies default token and revocation endpoints")
    void from_applies_default_token_endpoint() {
        // Given: OAuth2 config without explicit token endpoint
        OAuth2ConnectAuthenticationProperties props = mock(OAuth2ConnectAuthenticationProperties.class);

        UriTemplate authority = mock(UriTemplate.class);
        when(authority.getLiteralUri()).thenReturn(URI.create("https://auth.example.com"));
        when(props.getAuthority()).thenReturn(authority);

        OAuth2AuthenticationDataClient client = mock(OAuth2AuthenticationDataClient.class);
        when(client.getId()).thenReturn("my-client");
        when(client.getSecret()).thenReturn("my-secret");
        when(props.getClient()).thenReturn(client);

        when(props.getGrant()).thenReturn(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);

        // No endpoints specified
        when(props.getEndpoints()).thenReturn(null);

        // When
        EndpointKey key = EndpointKey.from(props);

        // Then: Should apply CNCF spec defaults for supported endpoints
        assertThat(key.tokenPath()).isEqualTo("/oauth2/token");
        assertThat(key.revocationPath()).isEqualTo("/oauth2/revoke");
    }

    @Test
    @DisplayName("fromNonResolved() - creates new key from unresolved template")
    void from_non_resolved_creates_new_key() {
        // Given: Unresolved endpoint key (with expression placeholders)
        EndpointKey unresolvedKey = new EndpointKey(
                "https://auth.example.com",
                "/oauth2/token",
                "/oauth2/revoke",
                false,
                "client-id-placeholder",
                "secret-placeholder",
                OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_POST,
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                List.of("read"),
                List.of("api://example"));

        // When: Create resolved key from runtime values
        EndpointKey resolved = EndpointKey.fromNonResolved(
                "https://resolved-auth.example.com",
                "resolved-client-id",
                "resolved-secret",
                unresolvedKey);

        // Then: Should have resolved values for credentials/authority
        assertThat(resolved.authority()).isEqualTo("https://resolved-auth.example.com");
        assertThat(resolved.clientId()).isEqualTo("resolved-client-id");
        assertThat(resolved.clientSecret()).isEqualTo("resolved-secret");

        // And preserve other fields from unresolved key
        assertThat(resolved.tokenPath()).isEqualTo("/oauth2/token");
        assertThat(resolved.revocationPath()).isEqualTo("/oauth2/revoke");
        assertThat(resolved.grant()).isEqualTo(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
        assertThat(resolved.scopes()).containsExactly("read");
        assertThat(resolved.audiences()).containsExactly("api://example");
    }

    @Test
    @DisplayName("equals() - same endpoint config with different username/password produces equal keys")
    void equals_same_config_different_username_password() {
        // Given: Two PASSWORD grant configs with same endpoint but different username/password
        OAuth2ConnectAuthenticationProperties props1 = createOAuth2PropsWithPassword(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                "alice",
                "password123");

        OAuth2ConnectAuthenticationProperties props2 = createOAuth2PropsWithPassword(
                "https://auth.example.com",
                "my-client",
                "my-secret",
                "bob",
                "secret456");

        // When
        EndpointKey key1 = EndpointKey.from(props1);
        EndpointKey key2 = EndpointKey.from(props2);

        // Then: Should be EQUAL - username/password are NOT part of client identity
        // They're passed as dynamic grant params at token request time
        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    // Helper methods

    private OAuth2ConnectAuthenticationProperties createOAuth2Props(
            String authority,
            String clientId,
            String clientSecret,
            OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant,
            List<String> scopes,
            List<String> audiences) {

        OAuth2ConnectAuthenticationProperties props = mock(OAuth2ConnectAuthenticationProperties.class);

        UriTemplate authorityTemplate = mock(UriTemplate.class);
        when(authorityTemplate.getLiteralUri()).thenReturn(URI.create(authority));
        when(props.getAuthority()).thenReturn(authorityTemplate);

        OAuth2AuthenticationDataClient client = mock(OAuth2AuthenticationDataClient.class);
        when(client.getId()).thenReturn(clientId);
        when(client.getSecret()).thenReturn(clientSecret);
        when(props.getClient()).thenReturn(client);

        when(props.getGrant()).thenReturn(grant);

        OAuth2AuthenticationPropertiesEndpoints endpoints = mock(OAuth2AuthenticationPropertiesEndpoints.class);
        when(endpoints.getToken()).thenReturn("/oauth2/token");
        when(endpoints.getRevocation()).thenReturn("/oauth2/revoke");
        when(props.getEndpoints()).thenReturn(endpoints);

        if (scopes != null) {
            when(props.getScopes()).thenReturn(scopes);
        }
        if (audiences != null) {
            when(props.getAudiences()).thenReturn(audiences);
        }

        return props;
    }

    private OAuth2ConnectAuthenticationProperties createOAuth2PropsWithPassword(
            String authority,
            String clientId,
            String clientSecret,
            String username,
            String password) {

        OAuth2ConnectAuthenticationProperties props = mock(OAuth2ConnectAuthenticationProperties.class);

        UriTemplate authorityTemplate = mock(UriTemplate.class);
        when(authorityTemplate.getLiteralUri()).thenReturn(URI.create(authority));
        when(props.getAuthority()).thenReturn(authorityTemplate);

        OAuth2AuthenticationDataClient client = mock(OAuth2AuthenticationDataClient.class);
        when(client.getId()).thenReturn(clientId);
        when(client.getSecret()).thenReturn(clientSecret);
        when(props.getClient()).thenReturn(client);

        when(props.getGrant()).thenReturn(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.PASSWORD);
        when(props.getUsername()).thenReturn(username);
        when(props.getPassword()).thenReturn(password);

        OAuth2AuthenticationPropertiesEndpoints endpoints = mock(OAuth2AuthenticationPropertiesEndpoints.class);
        when(endpoints.getToken()).thenReturn("/oauth2/token");
        when(endpoints.getRevocation()).thenReturn("/oauth2/revoke");
        when(props.getEndpoints()).thenReturn(endpoints);

        return props;
    }
}
