package io.quarkiverse.flow.oidc.impl;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.quarkiverse.flow.oidc.registry.EndpointKey;
import io.quarkiverse.flow.oidc.registry.OidcClientRegistry;
import io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar;
import io.quarkiverse.flow.oidc.registry.OidcConfigResolver;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.auth.AuthProvider;
import io.smallrye.mutiny.Uni;

/**
 * An {@link AuthProvider} that negotiates an OAuth2/OIDC access token using a Quarkus {@link OidcClient}.
 *
 * <p>
 * OIDC clients are created eagerly when workflows are registered and stored in {@link OidcClientRegistry}.
 * At runtime, this provider resolves the client name using the same routing logic as registration,
 * retrieves the client from the registry, and negotiates an access token.
 *
 * <p>
 * For the token-exchange grant, per-execution subject/actor tokens are resolved from the workflow context
 * and passed as dynamic grant parameters. The negotiated {@code access_token} is returned and the SDK
 * attaches it as {@code Authorization: Bearer <token>} to the downstream call.
 */
public final class OidcClientAuthProvider implements AuthProvider {

    private final OidcClientRegistry clientRegistry;
    private final OidcConfigResolver configResolver;
    private final OidcClientWorkflowRegistrar clientWorkflowRegistrar;
    private final String authPolicyName;

    private final WorkflowValueResolver<EndpointKey> endPointKeyResolver;
    private final WorkflowValueResolver<Map<String, String>> paramsResolver;

    public OidcClientAuthProvider(
            String authPolicyName,
            OidcClientRegistry clientRegistry,
            OidcConfigResolver configResolver,
            OidcClientWorkflowRegistrar clientWorkflowRegistrar,
            WorkflowValueResolver<EndpointKey> endPointKeyResolver,
            WorkflowValueResolver<Map<String, String>> paramsResolver) {
        this.clientRegistry = clientRegistry;
        this.configResolver = configResolver;
        this.clientWorkflowRegistrar = clientWorkflowRegistrar;
        this.authPolicyName = authPolicyName;
        this.endPointKeyResolver = endPointKeyResolver;
        this.paramsResolver = paramsResolver;
    }

    @Override
    public String scheme() {
        return "Bearer";
    }

    @Override
    public CompletableFuture<String> content(WorkflowContext workflow, TaskContext task, WorkflowModel model, URI uri) {
        // First get the configured static OidcClients built in build-time or configured by users
        OidcClient client = clientRegistry.get(configResolver.resolveOidcClientName(workflow.definition().id(), task.taskName(),
                authPolicyName).orElse(null));
        Duration connectionTimeout = configResolver.resolveConnectionTimeout(
                workflow.definition().id(), task.taskName(), authPolicyName);
        // Let's try to configure/find the OidcClient in runtime (might require runtime expression evaluation)
        if (client == null) {
            final EndpointKey endpointKey = endPointKeyResolver.apply(workflow, task, model);
            client = clientRegistry.getByEndpoint(endpointKey);
            if (client == null) {
                client = clientWorkflowRegistrar.registerDynamicOidcClientFor(endpointKey,
                        configResolver.resolveCreationTimeout(
                                workflow.definition().id(), task.taskName(), authPolicyName),
                        connectionTimeout);
                if (client == null) {
                    throw new IllegalStateException(
                            "Unable to create OIDC client for " + workflow.definition().id() + ", task: "
                                    + task.taskName() + " to access URI " + uri);
                }
            }
        }
        // Resolve dynamic grant parameters (for token exchange)
        final Map<String, String> dynamicParams = paramsResolver.apply(workflow, task, model);
        final Uni<Tokens> tokens = dynamicParams.isEmpty()
                ? client.getTokens()
                : client.getTokens(dynamicParams);
        return tokens.ifNoItem().after(connectionTimeout).fail().subscribeAsCompletionStage()
                .thenApply(t -> t.getAccessToken());
    }
}
