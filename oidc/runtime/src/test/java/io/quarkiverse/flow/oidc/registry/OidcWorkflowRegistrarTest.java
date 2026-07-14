package io.quarkiverse.flow.oidc.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.quarkiverse.flow.oidc.FlowOidcConfig;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.smallrye.mutiny.Uni;

class OidcWorkflowRegistrarTest {

    private OidcClientWorkflowRegistrar listener;
    private OidcClients oidcClients;
    private OidcClientRegistry registry;
    private FlowOidcConfig config;
    private OidcConfigResolver configResolver;

    @BeforeEach
    void setUp() {
        oidcClients = mock(OidcClients.class);
        registry = mock(OidcClientRegistry.class);
        config = mock(FlowOidcConfig.class);
        configResolver = mock(OidcConfigResolver.class);

        when(config.connectionTimeout()).thenReturn(Duration.ofSeconds(10));
        when(config.creationTimeout()).thenReturn(Duration.ofSeconds(10));

        // Mock successful client creation
        OidcClient mockClient = mock(OidcClient.class);
        Uni<OidcClient> clientUni = Uni.createFrom().item(mockClient);
        when(oidcClients.newClient(any())).thenReturn(clientUni);

        listener = new OidcClientWorkflowRegistrar();
        listener.oidcClients = oidcClients;
        listener.registry = registry;
        listener.config = config;
        listener.configResolver = configResolver;
    }

    @Test
    @DisplayName("Named policy without routing override - uses policy name")
    void named_policy_no_override_uses_policy_name() {
        // Given: workflow with named policy "keycloak"
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0")
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", a -> a.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)))))
                .build();

        WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);

        // No routing override
        when(configResolver.resolve(eq(workflowId), eq(null), eq("keycloak")))
                .thenReturn(Optional.empty());

        // When
        listener.registerStaticOidcClientsFor(workflow);

        // Then: registry should be called with "keycloak"
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(registry).register(nameCaptor.capture(), any(), any());
        assertThat(nameCaptor.getValue()).isEqualTo("keycloak");
    }

    @Test
    @DisplayName("Named policy with routing override - uses override name")
    void named_policy_with_override_uses_override_name() {
        // Given: workflow with named policy "keycloak"
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0")
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", a -> a.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)))))
                .build();

        WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);

        // Routing override to "prodKeycloak"
        when(configResolver.resolve(eq(workflowId), eq(null), eq("keycloak")))
                .thenReturn(Optional.of("prodKeycloak"));

        // When
        listener.registerStaticOidcClientsFor(workflow);

        // Then: registry should be called with "prodKeycloak"
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(registry).register(nameCaptor.capture(), any(), any());
        assertThat(nameCaptor.getValue()).isEqualTo("prodKeycloak");
    }

    @Test
    @DisplayName("Inline auth without routing override - uses composite name")
    void inline_auth_no_override_uses_composite_name() {
        // Given: workflow with inline auth on task "payment"
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0")
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment")
                                .POST()
                                .uri(URI.create("https://api.example.com/payment"),
                                        FuncDSL.oauth2("https://auth.example.com",
                                                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                                "client", "secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);

        // No routing override (task name is auto-generated, use any())
        when(configResolver.resolve(eq(workflowId), any(String.class), eq(null)))
                .thenReturn(Optional.empty());

        // When
        listener.registerStaticOidcClientsFor(workflow);

        // Then: registry should be called with composite name (task name is auto-generated by DSL)
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(registry).register(nameCaptor.capture(), any(), any());
        assertThat(nameCaptor.getValue()).startsWith("acme:orders:1.0.0.task.");
    }

    @Test
    @DisplayName("Inline auth with task-level routing override - uses override name")
    void inline_auth_with_task_override_uses_override_name() {
        // Given: workflow with inline auth on task "payment"
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0")
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment")
                                .POST()
                                .uri(URI.create("https://api.example.com/payment"),
                                        FuncDSL.oauth2("https://auth.example.com",
                                                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                                "client", "secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);

        // Routing override via quarkus.flow.oidc.orders.task.payment.name=customPaymentAuth
        when(configResolver.resolve(eq(workflowId), any(String.class), eq(null)))
                .thenReturn(Optional.of("customPaymentAuth"));

        // When
        listener.registerStaticOidcClientsFor(workflow);

        // Then: registry should be called with "customPaymentAuth"
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(registry).register(nameCaptor.capture(), any(), any());
        assertThat(nameCaptor.getValue()).isEqualTo("customPaymentAuth");
    }

    @Test
    @DisplayName("Inline auth with workflow-level routing override - uses override name")
    void inline_auth_with_workflow_override_uses_override_name() {
        // Given: workflow with inline auth on task "payment"
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0")
                .tasks(FuncDSL.call(
                        FuncDSL.http("payment")
                                .POST()
                                .uri(URI.create("https://api.example.com/payment"),
                                        FuncDSL.oauth2("https://auth.example.com",
                                                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                                "client", "secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);

        // Routing override via quarkus.flow.oidc.orders.name=ordersAuth
        when(configResolver.resolve(eq(workflowId), any(String.class), eq(null)))
                .thenReturn(Optional.of("ordersAuth"));

        // When
        listener.registerStaticOidcClientsFor(workflow);

        // Then: registry should be called with "ordersAuth"
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(registry).register(nameCaptor.capture(), any(), any());
        assertThat(nameCaptor.getValue()).isEqualTo("ordersAuth");
    }

    @Test
    @DisplayName("User-configured client exists - skips creation but registers policy")
    void user_configured_client_exists_skips_creation() {
        // Given: workflow with named policy "keycloak"
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0")
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", a -> a.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)))))
                .build();

        WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);

        // No routing override
        when(configResolver.resolve(eq(workflowId), eq(null), eq("keycloak")))
                .thenReturn(Optional.empty());

        // User already configured "keycloak" client - skip via registry check
        OidcClient existingClient = mock(OidcClient.class);
        when(registry.get("keycloak")).thenReturn(existingClient);

        // When
        listener.registerStaticOidcClientsFor(workflow);

        // Then: should skip creation but still register policy for endpoint matching
        verify(oidcClients, never()).newClient(any());
        verify(registry, times(1)).register(eq("keycloak"), eq(existingClient), any(EndpointKey.class));
    }

    @Test
    @DisplayName("Client already in registry - skips creation but registers policy")
    void client_already_in_registry_skips_creation() {
        // Given: workflow with named policy "keycloak"
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0")
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", a -> a.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)))))
                .build();

        WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);

        // No routing override
        when(configResolver.resolve(eq(workflowId), eq(null), eq("keycloak")))
                .thenReturn(Optional.empty());

        // Not in user config
        when(oidcClients.getClient("keycloak")).thenReturn(null);

        // But already in our registry
        OidcClient existingClient = mock(OidcClient.class);
        when(registry.get("keycloak")).thenReturn(existingClient);

        // When
        listener.registerStaticOidcClientsFor(workflow);

        // Then: should skip creation but still register policy for endpoint matching
        verify(oidcClients, never()).newClient(any());
        verify(registry, times(1)).register(eq("keycloak"), eq(existingClient), any(EndpointKey.class));
    }

    @Test
    @DisplayName("Multiple policies in same workflow - all registered correctly")
    void multiple_policies_all_registered() {
        // Given: workflow with both named and inline auth
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0")
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", a -> a.oauth2(
                        oauth2 -> oauth2.authority("https://auth.example.com")
                                .grant(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)))))
                .tasks(
                        FuncDSL.call(FuncDSL.http("auth-task").uri(URI.create("https://api1.example.com"),
                                FuncDSL.use("keycloak"))),
                        FuncDSL.call(FuncDSL.http("payment")
                                .POST()
                                .uri(URI.create("https://api.example.com/payment"),
                                        FuncDSL.oauth2("https://auth.example.com",
                                                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                                "client", "secret",
                                                e -> e.token("/oauth2/token")))))
                .build();

        WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);

        // No routing overrides
        when(configResolver.resolve(eq(workflowId), eq(null), eq("keycloak")))
                .thenReturn(Optional.empty());
        when(configResolver.resolve(eq(workflowId), any(String.class), eq(null)))
                .thenReturn(Optional.empty());

        // When
        listener.registerStaticOidcClientsFor(workflow);

        // Then: both should be registered
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(registry, org.mockito.Mockito.times(2)).register(nameCaptor.capture(), any(), any());

        // Named policy uses exact name, inline uses auto-generated task name
        assertThat(nameCaptor.getAllValues()).contains("keycloak");
        assertThat(nameCaptor.getAllValues().stream().filter(n -> n.startsWith("acme:orders:1.0.0.task.")).count())
                .isEqualTo(1);
    }
}
