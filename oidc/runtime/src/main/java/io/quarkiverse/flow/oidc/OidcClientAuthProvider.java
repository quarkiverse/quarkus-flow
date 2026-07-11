package io.quarkiverse.flow.oidc;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;
import io.serverlessworkflow.api.types.OAuth2TokenDefinition;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowUtils;
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

    private final WorkflowApplication application;
    private final OAuth2AuthenticationData authData;
    private final OidcClient client;
    private final Duration connectionTimeout;

    public OidcClientAuthProvider(WorkflowApplication application, OAuth2AuthenticationData authData,
            OidcClient client, Duration connectionTimeout) {
        this.application = application;
        this.authData = authData;
        this.client = client;
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public String scheme() {
        return "Bearer";
    }

    @Override
    public String content(WorkflowContext workflow, TaskContext task, WorkflowModel model, URI uri) {
        // Resolve dynamic grant parameters (for token exchange)
        final Map<String, String> dynamicParams = resolveDynamicGrantParams(workflow, task, model);

        // Negotiate token (client was resolved at construction time)
        try {
            final Tokens tokens = dynamicParams.isEmpty()
                    ? client.getTokens().await().atMost(connectionTimeout)
                    : client.getTokens(dynamicParams).await().atMost(connectionTimeout);
            return tokens.getAccessToken();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Flow OIDC: failed to negotiate an access token: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Resolves dynamic grant parameters for token exchange grant.
     * Returns empty map for all other grant types.
     */
    private Map<String, String> resolveDynamicGrantParams(WorkflowContext workflow, TaskContext task, WorkflowModel model) {
        if (authData == null
                || authData.getGrant() != OAuth2AuthenticationDataGrant.URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE) {
            return Map.of();
        }

        final Map<String, String> params = new HashMap<>();
        addTokenParams(workflow, task, model, authData.getSubject(), "subject_token", "subject_token_type", params);
        addTokenParams(workflow, task, model, authData.getActor(), "actor_token", "actor_token_type", params);
        return params;
    }

    private void addTokenParams(WorkflowContext workflow, TaskContext task, WorkflowModel model,
            OAuth2TokenDefinition definition, String tokenKey, String typeKey, Map<String, String> params) {
        if (definition == null) {
            return;
        }
        final String token = resolve(workflow, task, model, definition.getToken());
        if (token != null) {
            params.put(tokenKey, token);
        }
        final String type = resolve(workflow, task, model, definition.getType());
        if (type != null) {
            params.put(typeKey, type);
        }
    }

    private String resolve(WorkflowContext workflow, TaskContext task, WorkflowModel model, String template) {
        if (template == null) {
            return null;
        }
        return WorkflowUtils.buildStringFilter(application, template).apply(workflow, task, model);
    }
}
