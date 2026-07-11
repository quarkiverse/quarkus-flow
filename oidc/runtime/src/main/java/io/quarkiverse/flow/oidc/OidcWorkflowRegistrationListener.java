package io.quarkiverse.flow.oidc;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowDescriptorRegisteredEvent;
import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@ApplicationScoped
@Unremovable
public class OidcWorkflowRegistrationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcWorkflowRegistrationListener.class.getName());

    @Inject
    OidcClients oidcClients;

    @Inject
    OidcClientRegistry registry;

    @Inject
    FlowOidcConfig config;

    @Inject
    OidcConfigResolver configResolver;

    private final Set<WorkflowDefinitionId> processedWorkflows = new HashSet<>();

    void onWorkflowRegistered(@Observes WorkflowDescriptorRegisteredEvent event) {
        this.processWorkflow(event.workflow());
    }

    public void processWorkflow(Workflow workflow) {
        final WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(workflow);
        if (processedWorkflows.contains(workflowId)) {
            // skips if we already processed it
            return;
        }

        final List<TokenAuthPolicy> policies = TokenAuthPolicyExtractor.extractTokenAuthPolicies(workflow);

        for (TokenAuthPolicy policy : policies) {
            try {
                createAndRegisterClient(workflowId, policy);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to create OIDC client for policy: " + policy.name(), e);
            }
        }
        processedWorkflows.add(workflowId);
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
            registry.register(clientName, existingClient, policy);
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
        registry.register(clientName, client, policy);

        if (overrideName.isPresent()) {
            LOGGER.info("Created runtime OIDC client: {} (routed from policy: {})", clientName, policy.name());
        } else {
            LOGGER.info("Created runtime OIDC client: {}", clientName);
        }
    }

}
