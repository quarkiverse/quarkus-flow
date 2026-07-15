package io.quarkiverse.flow.oidc.impl;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.oidc.registry.EndpointKey;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.expressions.ExpressionUtils;

/**
 * Resolves runtime expressions in OAuth2/OIDC authentication configurations.
 * <p>
 * This resolver evaluates workflow DSL expressions (like {@code ${ $secret.clientSecret }})
 * using the runtime workflow context, making secret values and workflow state available
 * for authentication.
 * <p>
 * <b>Example:</b>
 *
 * <pre>{@code
 * // Workflow DSL with expressions:
 * {
 *   "authority": "https://auth.example.com",
 *   "client": {
 *     "id": "${ $secret.oauth_client_id }",
 *     "secret": "${ $secret.oauth_client_secret }"
 *   }
 * }
 *
 * // At runtime, expressions are resolved to actual values:
 * // clientId = "my-app-client"
 * // clientSecret = "super-secret-value"
 * }</pre>
 * <p>
 * Resolved values are used to create an {@link EndpointKey} for client lookup/registration.
 */
@ApplicationScoped
@Unremovable
public class RuntimeExpressionResolver {

    @Inject
    WorkflowApplication application;

    private final ConcurrentHashMap<String, WorkflowValueResolver<String>> filterCache = new ConcurrentHashMap<>();

    private static String resolveAuthorityTemplate(UriTemplate authority) {
        if (authority == null) {
            throw new IllegalStateException("OAuth2/OIDC authentication policy is missing the required 'authority'");
        }

        final String value = authority.getLiteralUri() != null
                ? authority.getLiteralUri().toString()
                : authority.getLiteralUriTemplate();

        if (value == null || value.isBlank()) {
            throw new IllegalStateException("OAuth2/OIDC authentication policy authority cannot be null or blank");
        }
        return value;
    }

    /**
     * Resolves all expressions in the given OAuth2 authentication data and returns an EndpointKey
     * with resolved values.
     * <p>
     * This method:
     * <ol>
     * <li>Creates a non-resolved EndpointKey template (with expressions intact)</li>
     * <li>Resolves each field that may contain expressions (authority, clientId, clientSecret)</li>
     * <li>Returns a new EndpointKey with all expressions replaced by their runtime values</li>
     * </ol>
     * <p>
     * <b>Note:</b> Username and password are NOT included in EndpointKey. They are resolved
     * separately in {@link OidcClientAuthProvider#resolveDynamicGrantParams(WorkflowContext, TaskContext, WorkflowModel)} and
     * passed as
     * dynamic grant parameters at token request time.
     *
     * @param workflow the workflow execution context
     * @param task the task execution context
     * @param model the workflow data model
     * @param authenticationData the authentication configuration (may contain expressions)
     * @return an EndpointKey with all expressions resolved to literal values
     */
    public EndpointKey resolveAll(WorkflowContext workflow, TaskContext task, WorkflowModel model,
            OAuth2AuthenticationData authenticationData) {
        final EndpointKey nonResolvedEndpointKey = EndpointKey.from(authenticationData);

        final String authority = resolveOne(workflow, task, model, resolveAuthorityTemplate(authenticationData.getAuthority()));
        final String clientId = resolveOne(workflow, task, model, nonResolvedEndpointKey.clientId());
        final String clientSecret = resolveOne(workflow, task, model, nonResolvedEndpointKey.clientSecret());

        return EndpointKey.fromNonResolved(authority, clientId, clientSecret, nonResolvedEndpointKey);
    }

    public String resolveOne(WorkflowContext workflow, TaskContext task, WorkflowModel model, String template) {
        if (template == null) {
            return null;
        }

        if (!ExpressionUtils.isExpr(template)) {
            return template;
        }

        return filterCache.computeIfAbsent(template, k -> WorkflowUtils.buildStringFilter(application, template))
                .apply(workflow, task, model);
    }

}
