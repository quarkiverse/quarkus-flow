package io.quarkiverse.flow.oidc.registry;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.oidc.FlowOidcConfig;
import io.quarkiverse.flow.oidc.TokenAuthPolicy;
import io.quarkiverse.flow.oidc.TokenAuthPolicyExtractor;
import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@ApplicationScoped
@Unremovable
public class OidcClientWorkflowRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcClientWorkflowRegistrar.class.getName());
    private final Set<WorkflowDefinitionId> processedWorkflows = new HashSet<>();

    @Inject
    OidcClients oidcClients;
    @Inject
    OidcClientRegistry registry;
    @Inject
    FlowOidcConfig config;
    @Inject
    OidcConfigResolver configResolver;

    /**
     * Register static OAuth2 or OIDC definitions from the given workflow descriptor into {@link OidcClientRegistry}
     */
    public void registerStaticOidcClientsFor(Workflow workflow) {
        final WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);
        // guardrail for concurrency
        if (!processedWorkflows.add(workflowId))
            // skips if we already processed it
            return;

        try {
            final List<TokenAuthPolicy> policies = TokenAuthPolicyExtractor.extractStaticTokenAuthPolicies(workflow);

            for (TokenAuthPolicy policy : policies) {
                try {
                    createAndRegisterClient(workflowId, policy);
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Failed to create OIDC client for policy: " + policy.name(), e);
                }
            }
            processedWorkflows.add(workflowId);
        } catch (Exception e) {
            processedWorkflows.remove(workflowId);
            throw e;
        }
    }

    public OidcClient registerDynamicOidcClientFor(EndpointKey endpointKey) {
        final OidcClientConfig clientConfig = OidcClientConfigFactory.from(endpointKey, config.connectionTimeout());
        final OidcClient client = oidcClients.newClient(clientConfig)
                .await()
                .atMost(config.creationTimeout());
        LOGGER.debug("Registering OIDC client '{}' with EndpointKey: {}", endpointKey.oidcId(), endpointKey);
        registry.register(endpointKey.oidcId(), client, endpointKey);
        return client;
    }

    private void createAndRegisterClient(WorkflowDefinitionId workflowId, TokenAuthPolicy policy) {
        final String taskName = policy.taskName().orElse(null);
        final String authPolicyName = taskName == null ? policy.name() : null;

        // Resolve client name (respecting user routing config)
        final Optional<String> overrideName = configResolver.resolve(workflowId, taskName, authPolicyName);
        final String clientName = overrideName.orElse(policy.name());

        final OidcClient existingClient = registry.get(clientName);
        if (existingClient != null) {
            EndpointKey endpointKey = policy.endpointKey();
            LOGGER.debug("Client '{}' already exists, registering policy with EndpointKey: {}", clientName, endpointKey);
            // Client exists (pre-configured or from earlier registration), but we still need to
            // register this policy for endpoint-based matching in the factory
            registry.register(clientName, existingClient, endpointKey);
            return;
        }

        // Create client
        final OidcClientConfig clientConfig = OidcClientConfigFactory.from(policy, config.connectionTimeout());
        final OidcClient client = oidcClients.newClient(clientConfig)
                .await()
                .atMost(config.creationTimeout());

        // Register with resolved name and policy (for endpoint-based lookup)
        EndpointKey endpointKey = policy.endpointKey();
        LOGGER.debug("Registering OIDC client '{}' with EndpointKey: {}", clientName, endpointKey);
        registry.register(clientName, client, endpointKey);

        if (overrideName.isPresent()) {
            LOGGER.info("Created runtime OIDC client: {} (routed from policy: {})", clientName, policy.name());
        } else {
            LOGGER.info("Created runtime OIDC client: {}", clientName);
        }
    }

}
