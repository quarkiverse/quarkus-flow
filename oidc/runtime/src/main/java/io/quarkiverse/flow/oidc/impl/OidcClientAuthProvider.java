package io.quarkiverse.flow.oidc.impl;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.quarkiverse.flow.oidc.registry.EndpointKey;
import io.quarkiverse.flow.oidc.registry.OidcClientRegistry;
import io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar;
import io.quarkiverse.flow.oidc.registry.OidcConfigResolver;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;
import io.serverlessworkflow.api.types.OAuth2TokenDefinition;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.auth.AuthProvider;

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

    private final OAuth2AuthenticationData authData;
    private final OidcClientRegistry clientRegistry;
    private final OidcConfigResolver configResolver;
    private final OidcClientWorkflowRegistrar clientWorkflowRegistrar;
    private final String authPolicyName;
    private final RuntimeExpressionResolver expressionResolver;

    public OidcClientAuthProvider(OAuth2AuthenticationData authData,
            String authPolicyName,
            OidcClientRegistry clientRegistry,
            OidcConfigResolver configResolver,
            OidcClientWorkflowRegistrar clientWorkflowRegistrar,
            RuntimeExpressionResolver expressionResolver) {
        this.authData = authData;
        this.clientRegistry = clientRegistry;
        this.configResolver = configResolver;
        this.clientWorkflowRegistrar = clientWorkflowRegistrar;
        this.authPolicyName = authPolicyName;
        this.expressionResolver = expressionResolver;
    }

    @Override
    public String scheme() {
        return "Bearer";
    }

    @Override
    public String content(WorkflowContext workflow, TaskContext task, WorkflowModel model, URI uri) {
        // First get the configured static OidcClients built in build-time or configured by users
        final Optional<String> namedOidc = configResolver.resolveOidcClientName(workflow.definition().id(), task.taskName(),
                authPolicyName);
        OidcClient client = clientRegistry.get(namedOidc.orElse(null));

        // Let's try to configure/find the OidcClient in runtime (might require runtime expression evaluation)
        if (client == null) {
            final EndpointKey endpointKey = expressionResolver.resolveAll(workflow, task, model, authData);
            client = clientRegistry.getByEndpoint(endpointKey);
            if (client == null) {
                // Resolve both timeouts using cascade logic
                final Duration creationTimeout = configResolver.resolveCreationTimeout(
                        workflow.definition().id(), task.taskName(), authPolicyName);
                final Duration connectionTimeout = configResolver.resolveConnectionTimeout(
                        workflow.definition().id(), task.taskName(), authPolicyName);

                client = clientWorkflowRegistrar.registerDynamicOidcClientFor(endpointKey,
                        creationTimeout, connectionTimeout);
            }
            if (client == null) {
                throw new IllegalStateException("Unable to create OIDC client for " + workflow.definition().id() + ", task: "
                        + task.taskName() + " to access URI " + uri);
            }
        }

        // Resolve dynamic grant parameters (for token exchange)
        final Map<String, String> dynamicParams = resolveDynamicGrantParams(workflow, task, model);

        // Resolve connection timeout: named clients use their own timeout, others use cascade resolution
        final Duration connectionTimeout = namedOidc.isPresent()
                ? configResolver.namedConnectionTimeout(namedOidc.get())
                : configResolver.resolveConnectionTimeout(workflow.definition().id(), task.taskName(), authPolicyName);

        final Tokens tokens = dynamicParams.isEmpty()
                ? client.getTokens().await().atMost(connectionTimeout)
                : client.getTokens(dynamicParams).await().atMost(connectionTimeout);
        return tokens.getAccessToken();
    }

    /**
     * Resolves dynamic grant parameters for PASSWORD and TOKEN_EXCHANGE grants.
     * Returns empty map for all other grant types.
     */
    private Map<String, String> resolveDynamicGrantParams(WorkflowContext workflow, TaskContext task, WorkflowModel model) {
        if (authData == null) {
            return Map.of();
        }

        final Map<String, String> params = new HashMap<>();

        if (authData.getGrant() == OAuth2AuthenticationDataGrant.PASSWORD) {
            // PASSWORD grant requires username and password in the token request
            final String username = expressionResolver.resolveOne(workflow, task, model, authData.getUsername());
            final String password = expressionResolver.resolveOne(workflow, task, model, authData.getPassword());
            if (username != null) {
                params.put("username", username);
            }
            if (password != null) {
                params.put("password", password);
            }
        } else if (authData.getGrant() == OAuth2AuthenticationDataGrant.URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE) {
            // TOKEN_EXCHANGE grant requires subject and actor tokens
            addTokenParams(workflow, task, model, authData.getSubject(), "subject_token", "subject_token_type", params);
            addTokenParams(workflow, task, model, authData.getActor(), "actor_token", "actor_token_type", params);
        }

        return params;
    }

    private void addTokenParams(WorkflowContext workflow, TaskContext task, WorkflowModel model,
            OAuth2TokenDefinition definition, String tokenKey, String typeKey, Map<String, String> params) {
        if (definition == null) {
            return;
        }
        final String token = expressionResolver.resolveOne(workflow, task, model, definition.getToken());
        if (token != null) {
            params.put(tokenKey, token);
        }
        final String type = expressionResolver.resolveOne(workflow, task, model, definition.getType());
        if (type != null) {
            params.put(typeKey, type);
        }
    }

}
