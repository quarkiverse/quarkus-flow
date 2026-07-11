package io.quarkiverse.flow.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;
import io.serverlessworkflow.api.types.OAuth2TokenDefinition;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.mutiny.Uni;

class OidcClientAuthProviderTest {

    private OidcClientAuthProvider provider;
    private WorkflowApplication application;
    private OAuth2AuthenticationData authData;
    private OidcClient mockClient;
    private Tokens mockTokens;
    private Duration timeout;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        application = mock(WorkflowApplication.class);
        authData = null; // Set per test
        mockClient = mock(OidcClient.class);
        mockTokens = mock(Tokens.class);
        timeout = Duration.ofSeconds(10);

        // Default mocks
        when(mockTokens.getAccessToken()).thenReturn("mock-access-token");
        Uni<Tokens> tokenUni = Uni.createFrom().item(mockTokens);
        when(mockClient.getTokens()).thenReturn(tokenUni);
        when(mockClient.getTokens(any(Map.class))).thenReturn(tokenUni);
    }

    @Test
    @DisplayName("Uses resolved client to negotiate token")
    void uses_resolved_client_to_negotiate_token() {
        // Given
        authData = mockOAuth2Data(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
        provider = new OidcClientAuthProvider(application, authData, mockClient, timeout);

        WorkflowContext workflowCtx = mockWorkflowContext("acme", "orders", "1.0.0");
        TaskContext taskCtx = mockTaskContext("payment-task");

        // When
        String token = provider.content(workflowCtx, taskCtx, mock(WorkflowModel.class), URI.create("https://api.example.com"));

        // Then
        assertThat(token).isEqualTo("mock-access-token");
        verify(mockClient).getTokens();
    }

    @Test
    @DisplayName("Token negotiation failure - throws exception")
    void token_negotiation_failure_throws_exception() {
        // Given
        authData = mockOAuth2Data(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
        provider = new OidcClientAuthProvider(application, authData, mockClient, timeout);

        WorkflowContext workflowCtx = mockWorkflowContext("acme", "orders", "1.0.0");
        TaskContext taskCtx = mockTaskContext("payment-task");

        // Simulate token negotiation failure
        when(mockClient.getTokens()).thenReturn(Uni.createFrom().failure(new RuntimeException("Auth server down")));

        // When/Then
        assertThatThrownBy(
                () -> provider.content(workflowCtx, taskCtx, mock(WorkflowModel.class), URI.create("https://api.example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to negotiate an access token");
    }

    @Test
    @DisplayName("Token exchange grant - passes dynamic parameters")
    @SuppressWarnings("unchecked")
    void token_exchange_grant_passes_dynamic_params() {
        // Given: token exchange grant with subject/actor tokens
        WorkflowContext workflowCtx = mockWorkflowContext("acme", "orders", "1.0.0");
        TaskContext taskCtx = mockTaskContext("payment-task");

        // Mock token exchange grant with subject/actor
        OAuth2ConnectAuthenticationProperties tokenExchangeData = mock(OAuth2ConnectAuthenticationProperties.class);
        when(tokenExchangeData.getGrant()).thenReturn(
                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE);

        OAuth2TokenDefinition subjectToken = mock(OAuth2TokenDefinition.class);
        when(subjectToken.getToken()).thenReturn("static-subject-token");
        when(subjectToken.getType()).thenReturn("urn:ietf:params:oauth:token-type:access_token");
        when(tokenExchangeData.getSubject()).thenReturn(subjectToken);

        OAuth2TokenDefinition actorToken = mock(OAuth2TokenDefinition.class);
        when(actorToken.getToken()).thenReturn("static-actor-token");
        when(actorToken.getType()).thenReturn("urn:ietf:params:oauth:token-type:access_token");
        when(tokenExchangeData.getActor()).thenReturn(actorToken);

        provider = new OidcClientAuthProvider(application, tokenExchangeData, mockClient, timeout);

        // When
        String token = provider.content(workflowCtx, taskCtx, mock(WorkflowModel.class), URI.create("https://api.example.com"));

        // Then
        assertThat(token).isEqualTo("mock-access-token");

        // Verify dynamic params were passed
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockClient).getTokens(paramsCaptor.capture());

        Map<String, String> params = paramsCaptor.getValue();
        assertThat(params).containsEntry("subject_token", "static-subject-token");
        assertThat(params).containsEntry("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        assertThat(params).containsEntry("actor_token", "static-actor-token");
        assertThat(params).containsEntry("actor_token_type", "urn:ietf:params:oauth:token-type:access_token");
    }

    @Test
    @DisplayName("Returns Bearer scheme")
    void returns_bearer_scheme() {
        authData = mockOAuth2Data(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
        provider = new OidcClientAuthProvider(application, authData, mockClient, timeout);
        assertThat(provider.scheme()).isEqualTo("Bearer");
    }

    // Helper methods

    private WorkflowContext mockWorkflowContext(String namespace, String name, String version) {
        WorkflowContext ctx = mock(WorkflowContext.class);
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        WorkflowDefinitionId id = new WorkflowDefinitionId(namespace, name, version);
        when(ctx.definition()).thenReturn(def);
        when(def.id()).thenReturn(id);
        return ctx;
    }

    private TaskContext mockTaskContext(String taskName) {
        TaskContext ctx = mock(TaskContext.class);
        when(ctx.taskName()).thenReturn(taskName);
        return ctx;
    }

    private OAuth2AuthenticationData mockOAuth2Data(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant) {
        OAuth2ConnectAuthenticationProperties data = mock(OAuth2ConnectAuthenticationProperties.class);
        when(data.getGrant()).thenReturn(grant);

        UriTemplate authority = mock(UriTemplate.class);
        when(authority.getLiteralUri()).thenReturn(URI.create("https://auth.example.com"));
        when(data.getAuthority()).thenReturn(authority);

        OAuth2AuthenticationDataClient client = mock(OAuth2AuthenticationDataClient.class);
        when(client.getId()).thenReturn("client-id");
        when(client.getSecret()).thenReturn("client-secret");
        when(data.getClient()).thenReturn(client);

        return data;
    }
}
