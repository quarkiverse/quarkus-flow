package io.quarkiverse.flow.oidc.impl;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.oidc.FlowOidcConfig;
import io.quarkiverse.flow.oidc.TokenAuthPolicy;
import io.quarkiverse.flow.oidc.TokenAuthPolicyExtractor;
import io.quarkiverse.flow.oidc.registry.EndpointKey;
import io.quarkiverse.flow.oidc.registry.OidcClientRegistry;
import io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar;
import io.quarkiverse.flow.oidc.registry.OidcConfigResolver;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.serverlessworkflow.api.types.EndpointConfiguration;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.auth.AuthProvider;

/**
 * Integration-style tests that verify the full contract between:
 * 1. Workflow registration → TokenAuthPolicy extraction
 * 2. Registry population with OIDC clients
 * 3. Factory endpoint matching and client retrieval
 */
class OidcAuthProviderFactoryIntegrationTest {

    private OidcClientRegistry registry;
    private OidcAuthProviderFactory factory;
    private FlowOidcConfig config;
    private OidcClientWorkflowRegistrar mockListener;
    private OidcClients mockOidcClients;

    @BeforeEach
    void setUp() {
        mockOidcClients = mock(OidcClients.class);
        registry = new OidcClientRegistry();
        // Use reflection to inject mock OidcClients
        try {
            var field = OidcClientRegistry.class.getDeclaredField("clients");
            field.setAccessible(true);
            field.set(registry, mockOidcClients);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        config = mock(FlowOidcConfig.class);
        when(config.connectionTimeout()).thenReturn(Duration.ofSeconds(10));

        mockListener = mock(OidcClientWorkflowRegistrar.class);
        RuntimeExpressionResolver mockExpressionResolver = mock(RuntimeExpressionResolver.class);
        OidcConfigResolver mockConfigResolver = mock(OidcConfigResolver.class);

        factory = new OidcAuthProviderFactory(registry, config, mockListener, mockExpressionResolver, mockConfigResolver);
    }

    @Test
    @DisplayName("End-to-end: Register workflow with inline OAuth2, factory retrieves client by endpoint")
    void end_to_end_inline_oauth2_registration_and_retrieval() {
        // Step 1: Create workflow with inline OAuth2 authentication
        Workflow workflow = FuncWorkflowBuilder.workflow("test")
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment-api")
                                .uri(URI.create("https://api.example.com/payments"),
                                        FuncDSL.oauth2("https://auth.example.com", CLIENT_CREDENTIALS, "my-client", "my-secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        // Step 2: Extract auth policies from workflow (simulating what the listener does)
        var policies = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);
        assertThat(policies).hasSize(1);

        TokenAuthPolicy policy = policies.get(0);
        // Name would be "test.task.payment-api" from extractor (no namespace/version in test workflow)

        // Step 3: Register OIDC client in registry (simulating what the listener does)
        OidcClient mockClient = mock(OidcClient.class);
        String clientName = policy.name();
        EndpointKey endpointKey = policy.endpointKey();
        registry.register(clientName, mockClient, endpointKey);

        // Step 4: Verify registry contains the client
        assertThat(registry.get(clientName)).isEqualTo(mockClient);

        // Step 5: Factory receives SDK auth request with same endpoint configuration
        // Extract the endpoint configuration from the workflow
        EndpointConfiguration endpoint = workflow.getDo().get(0)
                .getTask().getCallTask().getCallHTTP()
                .getWith().getEndpoint()
                .getEndpointConfiguration();

        // Step 6: Create mock workflow definition for factory
        WorkflowDefinition mockDefinition = mockWorkflowDefinition("acme", "test", "1.0.0");

        // Step 7: Factory should find the client and return OidcClientAuthProvider
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, endpoint);

        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(OidcClientAuthProvider.class);
    }

    @Test
    @DisplayName("End-to-end: Different credentials create different clients")
    void end_to_end_different_credentials_create_different_clients() {
        // Step 1: Create two workflows with same endpoint but different credentials
        Workflow workflow1 = FuncWorkflowBuilder.workflow("workflow1")
                .tasks(FuncDSL.call(
                        FuncDSL.http("api")
                                .uri(URI.create("https://api.example.com"),
                                        FuncDSL.oauth2("https://auth.example.com", CLIENT_CREDENTIALS, "client-A", "secret-A",
                                                e -> e.token("/oauth2/token")))))
                .build();

        Workflow workflow2 = FuncWorkflowBuilder.workflow("workflow2")
                .tasks(FuncDSL.call(
                        FuncDSL.http("api")
                                .uri(URI.create("https://api.example.com"),
                                        FuncDSL.oauth2("https://auth.example.com", CLIENT_CREDENTIALS, "client-B", "secret-B",
                                                e -> e.token("/oauth2/token")))))
                .build();

        // Step 2: Extract policies
        var policies1 = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow1);
        var policies2 = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow2);

        TokenAuthPolicy policy1 = policies1.get(0);
        TokenAuthPolicy policy2 = policies2.get(0);

        // Step 3: Verify different endpoint keys (different clientId and clientSecret)
        EndpointKey key1 = policy1.endpointKey();
        EndpointKey key2 = policy2.endpointKey();

        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1.clientId()).isEqualTo("client-A");
        assertThat(key2.clientId()).isEqualTo("client-B");

        // Step 4: Register both clients in registry
        OidcClient mockClient1 = mock(OidcClient.class);
        OidcClient mockClient2 = mock(OidcClient.class);

        registry.register(policy1.name(), mockClient1, policy1.endpointKey());
        registry.register(policy2.name(), mockClient2, policy2.endpointKey());

        // Step 5: Verify factory returns different clients for different endpoints
        WorkflowDefinition def1 = mockWorkflowDefinition("acme", "workflow1", "1.0.0");
        WorkflowDefinition def2 = mockWorkflowDefinition("acme", "workflow2", "1.0.0");

        EndpointConfiguration endpoint1 = workflow1.getDo().get(0)
                .getTask().getCallTask().getCallHTTP()
                .getWith().getEndpoint()
                .getEndpointConfiguration();

        EndpointConfiguration endpoint2 = workflow2.getDo().get(0)
                .getTask().getCallTask().getCallHTTP()
                .getWith().getEndpoint()
                .getEndpointConfiguration();

        Optional<AuthProvider> result1 = factory.getAuth(def1, endpoint1);
        Optional<AuthProvider> result2 = factory.getAuth(def2, endpoint2);

        assertThat(result1).isPresent();
        assertThat(result2).isPresent();

        // Both should be OidcClientAuthProvider but wrapping different clients
        assertThat(result1.get()).isInstanceOf(OidcClientAuthProvider.class);
        assertThat(result2.get()).isInstanceOf(OidcClientAuthProvider.class);

        // Verify they're different instances
        assertThat(result1.get()).isNotSameAs(result2.get());
    }

    @Test
    @DisplayName("End-to-end: Unregistered endpoint delegates to SDK")
    void end_to_end_unregistered_endpoint_delegates_to_sdk() {
        // Step 1: Create workflow with OAuth2
        Workflow workflow = FuncWorkflowBuilder.workflow("test")
                .tasks(FuncDSL.call(
                        FuncDSL.http("api")
                                .uri(URI.create("https://api.example.com"),
                                        FuncDSL.oauth2("https://auth.example.com", CLIENT_CREDENTIALS, "my-client", "my-secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        // Step 2: DON'T register it in the registry

        // Step 3: Factory receives SDK request for this endpoint
        WorkflowDefinition mockDefinition = mockWorkflowDefinition("acme", "test", "1.0.0");
        EndpointConfiguration endpoint = workflow.getDo().get(0)
                .getTask().getCallTask().getCallHTTP()
                .getWith().getEndpoint()
                .getEndpointConfiguration();

        // Step 4: Factory returns OidcClientAuthProvider (lazy registration will happen at runtime)
        Optional<AuthProvider> result = factory.getAuth(mockDefinition, endpoint);

        // Returns OidcClientAuthProvider even when client not pre-registered (lazy registration)
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(OidcClientAuthProvider.class);
    }

    // Helper methods

    private WorkflowDefinition mockWorkflowDefinition(String namespace, String name, String version) {
        return mockWorkflowDefinition(namespace, name, version, null);
    }

    private WorkflowDefinition mockWorkflowDefinition(String namespace, String name, String version, Workflow workflow) {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        WorkflowApplication app = mock(WorkflowApplication.class);

        when(def.id()).thenReturn(new WorkflowDefinitionId(namespace, name, version));
        when(def.application()).thenReturn(app);

        if (workflow != null) {
            when(def.workflow()).thenReturn(workflow);
        }

        return def;
    }
}
