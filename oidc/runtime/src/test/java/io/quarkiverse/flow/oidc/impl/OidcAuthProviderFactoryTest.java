package io.quarkiverse.flow.oidc.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.oidc.FlowOidcConfig;
import io.quarkiverse.flow.oidc.registry.EndpointKey;
import io.quarkiverse.flow.oidc.registry.OidcClientRegistry;
import io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar;
import io.quarkiverse.flow.oidc.registry.OidcConfigResolver;
import io.quarkus.oidc.client.OidcClient;
import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.EndpointConfiguration;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient;
import io.serverlessworkflow.api.types.OAuth2AuthenticationPropertiesEndpoints;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;
import io.serverlessworkflow.api.types.ReferenceableAuthenticationPolicy;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.Use;
import io.serverlessworkflow.api.types.UseAuthentications;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.auth.AuthProvider;

class OidcAuthProviderFactoryTest {

    private OidcAuthProviderFactory factory;
    private OidcClientRegistry mockRegistry;
    private FlowOidcConfig mockConfig;
    private OidcClientWorkflowRegistrar mockListener;
    private WorkflowDefinition mockDefinition;
    private WorkflowApplication mockApplication;
    private Workflow mockWorkflow;

    @BeforeEach
    void setUp() {
        mockRegistry = mock(OidcClientRegistry.class);
        mockListener = mock(OidcClientWorkflowRegistrar.class);

        RuntimeExpressionResolver mockResolver = mock(RuntimeExpressionResolver.class);
        OidcConfigResolver mockConfigResolver = mock(OidcConfigResolver.class);

        factory = new OidcAuthProviderFactory(mockRegistry, mockListener, mockResolver, mockConfigResolver);

        // Setup workflow definition mocks
        mockDefinition = mock(WorkflowDefinition.class);
        mockApplication = mock(WorkflowApplication.class);
        mockWorkflow = mock(Workflow.class);

        when(mockDefinition.id()).thenReturn(new WorkflowDefinitionId("acme", "orders", "1.0.0"));
        when(mockDefinition.application()).thenReturn(mockApplication);
        when(mockDefinition.workflow()).thenReturn(mockWorkflow);
    }

    @Test
    @DisplayName("getAuth(EndpointConfiguration) - inline OAuth2 with matching client returns provider")
    void getAuth_inline_oauth2_with_matching_client_returns_provider() {
        // Given: Inline OAuth2 endpoint configuration
        EndpointConfiguration endpoint = mockEndpointWithInlineOAuth2();
        OidcClient mockClient = mock(OidcClient.class);

        // Registry has matching client for this endpoint configuration
        when(mockRegistry.getByEndpoint(any(EndpointKey.class))).thenReturn(mockClient);

        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, endpoint);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(OidcClientAuthProvider.class);
    }

    @Test
    @DisplayName("getAuth(EndpointConfiguration) - inline OAuth2 with no matching client delegates to SDK")
    void getAuth_inline_oauth2_no_matching_client_delegates() {
        // Given: Inline OAuth2 endpoint configuration
        EndpointConfiguration endpoint = mockEndpointWithInlineOAuth2();

        // Registry has NO matching client
        when(mockRegistry.getByEndpoint(any(EndpointKey.class))).thenReturn(null);

        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, endpoint);

        // Then: Returns OidcClientAuthProvider (lazy registration will happen at runtime)
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(OidcClientAuthProvider.class);
    }

    @Test
    @DisplayName("getAuth(EndpointConfiguration) - null endpoint delegates to SDK")
    void getAuth_null_endpoint_delegates() {
        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAuth(EndpointConfiguration) - basic auth delegates to SDK")
    void getAuth_basic_auth_delegates() {
        // Given: Endpoint with basic auth (not OAuth2/OIDC)
        EndpointConfiguration endpoint = mockEndpointWithBasicAuth();

        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, endpoint);

        // Then: Should delegate to SDK (we don't handle basic auth)
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAuth(ReferenceableAuthenticationPolicy) - uses policy with matching client returns provider")
    void getAuth_uses_policy_with_matching_client_returns_provider() {
        // Given: Workflow with 'uses' auth reference
        Use mockUse = mockWorkflowUseWithOAuth2("my-oauth2");
        when(mockWorkflow.getUse()).thenReturn(mockUse);

        ReferenceableAuthenticationPolicy authRef = mockAuthReference("my-oauth2");

        // Registry has matching client
        OidcClient mockClient = mock(OidcClient.class);
        when(mockRegistry.getByEndpoint(any(EndpointKey.class))).thenReturn(mockClient);

        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, authRef, "GET");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(OidcClientAuthProvider.class);
    }

    @Test
    @DisplayName("getAuth(ReferenceableAuthenticationPolicy) - uses policy with no matching client delegates")
    void getAuth_uses_policy_no_matching_client_delegates() {
        // Given: Workflow with 'uses' auth reference
        Use mockUse = mockWorkflowUseWithOAuth2("my-oauth2");
        when(mockWorkflow.getUse()).thenReturn(mockUse);

        ReferenceableAuthenticationPolicy authRef = mockAuthReference("my-oauth2");

        // Registry has NO matching client
        when(mockRegistry.getByEndpoint(any(EndpointKey.class))).thenReturn(null);

        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, authRef, "GET");

        // Then: Returns OidcClientAuthProvider (lazy registration will happen at runtime)
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(OidcClientAuthProvider.class);
    }

    @Test
    @DisplayName("getAuth(ReferenceableAuthenticationPolicy) - null auth returns empty")
    void getAuth_null_auth_returns_empty() {
        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, null, "GET");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAuth(ReferenceableAuthenticationPolicy) - basic auth returns empty (EndpointKey is null)")
    void getAuth_basic_auth_endpoint_key_null() {
        // Given: Basic auth policy (not OAuth2/OIDC)
        ReferenceableAuthenticationPolicy basicAuth = mock(ReferenceableAuthenticationPolicy.class);
        AuthenticationPolicyUnion policyUnion = mock(AuthenticationPolicyUnion.class);

        // Basic auth has neither OAuth2 nor OIDC policies
        when(policyUnion.getOAuth2AuthenticationPolicy()).thenReturn(null);
        when(policyUnion.getOpenIdConnectAuthenticationPolicy()).thenReturn(null);
        when(basicAuth.getAuthenticationPolicy()).thenReturn(policyUnion);
        when(basicAuth.getAuthenticationPolicyReference()).thenReturn(null);

        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, basicAuth, "POST");

        // Then: Should not throw NPE, returns empty (delegates to SDK)
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAuth(ReferenceableAuthenticationPolicy) - inline policy with matching client returns provider")
    void getAuth_inline_policy_with_matching_client_returns_provider() {
        // Given: Inline auth policy (not 'uses' reference)
        ReferenceableAuthenticationPolicy inlineAuth = mockInlineOAuth2Policy();

        // Registry has matching client
        OidcClient mockClient = mock(OidcClient.class);
        when(mockRegistry.getByEndpoint(any(EndpointKey.class))).thenReturn(mockClient);

        // When
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, inlineAuth, "POST");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(OidcClientAuthProvider.class);
    }

    // Helper methods to create mock SDK objects

    private EndpointConfiguration mockEndpointWithInlineOAuth2() {
        EndpointConfiguration endpoint = mock(EndpointConfiguration.class);
        ReferenceableAuthenticationPolicy auth = mockInlineOAuth2Policy();
        when(endpoint.getAuthentication()).thenReturn(auth);
        return endpoint;
    }

    private EndpointConfiguration mockEndpointWithBasicAuth() {
        EndpointConfiguration endpoint = mock(EndpointConfiguration.class);
        ReferenceableAuthenticationPolicy auth = mock(ReferenceableAuthenticationPolicy.class);

        // Basic auth policy (not OAuth2/OIDC)
        AuthenticationPolicyUnion policyUnion = mock(AuthenticationPolicyUnion.class);
        when(policyUnion.getOAuth2AuthenticationPolicy()).thenReturn(null);
        when(policyUnion.getOpenIdConnectAuthenticationPolicy()).thenReturn(null);
        // Basic auth would have getBasicAuthenticationPolicy() != null

        when(auth.getAuthenticationPolicy()).thenReturn(policyUnion);
        when(auth.getAuthenticationPolicyReference()).thenReturn(null);
        when(endpoint.getAuthentication()).thenReturn(auth);
        return endpoint;
    }

    private ReferenceableAuthenticationPolicy mockInlineOAuth2Policy() {
        ReferenceableAuthenticationPolicy auth = mock(ReferenceableAuthenticationPolicy.class);
        AuthenticationPolicyUnion policyUnion = mockOAuth2PolicyUnion();

        when(auth.getAuthenticationPolicy()).thenReturn(policyUnion);
        when(auth.getAuthenticationPolicyReference()).thenReturn(null);

        return auth;
    }

    private AuthenticationPolicyUnion mockOAuth2PolicyUnion() {
        AuthenticationPolicyUnion policyUnion = mock(AuthenticationPolicyUnion.class);

        // Mock OAuth2 policy structure
        var oauth2Policy = mock(io.serverlessworkflow.api.types.OAuth2AuthenticationPolicy.class);
        var oauth2Config = mock(io.serverlessworkflow.api.types.OAuth2AuthenticationPolicyConfiguration.class);
        OAuth2ConnectAuthenticationProperties oauth2Props = mockOAuth2Properties();

        when(oauth2Config.getOAuth2ConnectAuthenticationProperties()).thenReturn(oauth2Props);
        when(oauth2Policy.getOauth2()).thenReturn(oauth2Config);
        when(policyUnion.getOAuth2AuthenticationPolicy()).thenReturn(oauth2Policy);
        when(policyUnion.getOpenIdConnectAuthenticationPolicy()).thenReturn(null);

        return policyUnion;
    }

    private OAuth2ConnectAuthenticationProperties mockOAuth2Properties() {
        OAuth2ConnectAuthenticationProperties props = mock(OAuth2ConnectAuthenticationProperties.class);

        UriTemplate authority = mock(UriTemplate.class);
        when(authority.getLiteralUri()).thenReturn(URI.create("https://auth.example.com"));
        when(props.getAuthority()).thenReturn(authority);

        OAuth2AuthenticationDataClient client = mock(OAuth2AuthenticationDataClient.class);
        when(client.getId()).thenReturn("test-client");
        when(client.getSecret()).thenReturn("test-secret");
        when(props.getClient()).thenReturn(client);

        when(props.getGrant()).thenReturn(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);

        OAuth2AuthenticationPropertiesEndpoints endpoints = mock(OAuth2AuthenticationPropertiesEndpoints.class);
        when(endpoints.getToken()).thenReturn("/oauth2/token");
        when(props.getEndpoints()).thenReturn(endpoints);

        return props;
    }

    private Use mockWorkflowUseWithOAuth2(String authName) {
        Use use = mock(Use.class);
        UseAuthentications auths = mock(UseAuthentications.class);

        AuthenticationPolicyUnion policyUnion = mockOAuth2PolicyUnion();

        when(auths.getAdditionalProperties()).thenReturn(java.util.Map.of(authName, policyUnion));
        when(use.getAuthentications()).thenReturn(auths);

        return use;
    }

    private ReferenceableAuthenticationPolicy mockAuthReference(String authName) {
        ReferenceableAuthenticationPolicy auth = mock(ReferenceableAuthenticationPolicy.class);
        var reference = mock(io.serverlessworkflow.api.types.AuthenticationPolicyReference.class);

        when(reference.getUse()).thenReturn(authName);
        when(auth.getAuthenticationPolicyReference()).thenReturn(reference);
        when(auth.getAuthenticationPolicy()).thenReturn(null);

        return auth;
    }
}
