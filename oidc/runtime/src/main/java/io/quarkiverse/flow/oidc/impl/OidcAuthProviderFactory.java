package io.quarkiverse.flow.oidc.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.quarkiverse.flow.oidc.registry.EndpointKey;
import io.quarkiverse.flow.oidc.registry.OidcClientRegistry;
import io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar;
import io.quarkiverse.flow.oidc.registry.OidcConfigResolver;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;
import io.serverlessworkflow.api.types.OAuth2TokenDefinition;
import io.serverlessworkflow.api.types.ReferenceableAuthenticationPolicy;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.auth.AuthProvider;
import io.serverlessworkflow.impl.auth.AuthProviderFactory;
import io.serverlessworkflow.impl.auth.DefaultAuthProviderFactory;
import io.serverlessworkflow.impl.auth.OAuthPolicyData;

/**
 * An {@link AuthProviderFactory} that routes OAuth2/OIDC token negotiation through Quarkus OIDC clients.
 * <p>
 * <b>Lifecycle & Timing:</b>
 * <p>
 * This factory is called by the Serverless Workflow SDK <b>once at application startup</b> per workflow,
 * not per HTTP request. When {@link #getAuth} is called:
 * <ol>
 * <li>Static OIDC clients (policies without runtime expressions) are registered eagerly via
 * {@link io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar#registerStaticOidcClientsFor}</li>
 * <li>An {@link OidcClientAuthProvider} is created and returned to the SDK</li>
 * <li>The SDK caches this provider and uses it for all subsequent workflow executions</li>
 * </ol>
 * <p>
 * Dynamic OIDC clients (policies with runtime expressions like {@code ${ $secret.xxx }}) are registered
 * lazily by {@link OidcClientAuthProvider} when the workflow executes and runtime context is available.
 * <p>
 * <b>Delegation:</b>
 * <p>
 * For authentication types this extension doesn't handle (basic, bearer, digest), requests are
 * delegated to the SDK's {@link DefaultAuthProviderFactory} to preserve existing behavior.
 *
 * @see OidcClientAuthProvider for runtime token negotiation
 * @see io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar for client registration logic
 */
public class OidcAuthProviderFactory extends DefaultAuthProviderFactory {

    private final OidcClientRegistry clientRegistry;
    private final OidcConfigResolver configResolver;
    private final OidcClientWorkflowRegistrar workflowRegistrar;

    private String authName;

    public OidcAuthProviderFactory(OidcClientRegistry clientRegistry,
            OidcClientWorkflowRegistrar workflowRegistrar,
            OidcConfigResolver configResolver) {
        this.clientRegistry = clientRegistry;
        this.workflowRegistrar = workflowRegistrar;
        this.configResolver = configResolver;
    }

    @Override
    public Optional<AuthProvider> getAuth(WorkflowDefinition definition, ReferenceableAuthenticationPolicy auth,
            String method) {
        this.authName = authPolicyName(auth);
        return super.getAuth(definition, auth, method);
    }

    private WorkflowValueResolver<EndpointKey> resolveEndpointKey(WorkflowApplication app, OAuth2AuthenticationData authData) {
        EndpointKey endpointKey = EndpointKey.from(authData);
        WorkflowValueResolver<String> authResolver = WorkflowUtils.buildStringFilter(app, endpointKey.authority());
        WorkflowValueResolver<String> clientIdFilter = WorkflowUtils.buildStringFilter(app, endpointKey.clientId());
        WorkflowValueResolver<String> clientSecretFilter = WorkflowUtils.buildStringFilter(app, endpointKey.clientSecret());
        return (w, t, m) -> EndpointKey.fromNonResolved(authResolver.apply(w, t, m), clientIdFilter.apply(w, t, m),
                clientSecretFilter.apply(w, t, m), endpointKey);
    }

    private WorkflowValueResolver<Map<String, String>> resolveDynamicGrantParams(WorkflowApplication app,
            OAuth2AuthenticationData authData) {
        if (authData.getGrant() == OAuth2AuthenticationDataGrant.PASSWORD) {
            WorkflowValueResolver<String> userFilter = WorkflowUtils.buildStringFilter(app, authData.getUsername());
            WorkflowValueResolver<String> passwordFilter = WorkflowUtils.buildStringFilter(app, authData.getPassword());
            return (workflow, task, model) -> {
                Map<String, String> params = new HashMap<>();
                String username = userFilter.apply(workflow, task, model);
                if (username != null) {
                    params.put("username", username);
                }
                String password = passwordFilter.apply(workflow, task, model);
                if (password != null) {
                    params.put("password", password);
                }
                return params;
            };
        } else if (authData.getGrant() == OAuth2AuthenticationDataGrant.URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE) {
            // TOKEN_EXCHANGE grant requires subject and actor tokens
            Optional<WorkflowValueResolver<Map<String, String>>> subjectResolver = resolveTokenParam(app, authData.getSubject(),
                    "subject_token", "subject_token_type");
            Optional<WorkflowValueResolver<Map<String, String>>> actorResolver = resolveTokenParam(app, authData.getActor(),
                    "actor_token", "actor_token_type");
            return (workflow, task, model) -> {
                Map<String, String> params = new HashMap<>();
                subjectResolver.ifPresent(resolver -> params.putAll(resolver.apply(workflow, task, model)));
                actorResolver.ifPresent(resolver -> params.putAll(resolver.apply(workflow, task, model)));
                return params;
            };
        }
        return (workflow, task, model) -> Map.of();

    }

    private Optional<WorkflowValueResolver<Map<String, String>>> resolveTokenParam(
            WorkflowApplication app, OAuth2TokenDefinition definition, String tokenKey, String typeKey) {
        if (definition == null) {
            return Optional.empty();
        }
        WorkflowValueResolver<String> tokenFilter = WorkflowUtils.buildStringFilter(app, definition.getToken());
        WorkflowValueResolver<String> typeFilter = WorkflowUtils.buildStringFilter(app, definition.getType());
        return Optional.of((workflow, task, model) -> {
            Map<String, String> params = new HashMap<>();
            final String token = tokenFilter.apply(workflow, task, model);
            if (token != null) {
                params.put(tokenKey, token);
            }
            final String type = typeFilter.apply(workflow, task, model);
            if (type != null) {
                params.put(typeKey, type);
            }
            return params;
        });
    }

    @Override
    protected AuthProvider oAuth2AuthProvider(
            WorkflowApplication app, Workflow workflow, OAuthPolicyData policyData) {
        return build(app, workflow, policyData);
    }

    @Override
    protected AuthProvider openIdAuthProvider(
            WorkflowApplication app, Workflow workflow, OAuthPolicyData policyData) {
        return build(app, workflow, policyData);
    }

    private AuthProvider build(WorkflowApplication app, Workflow workflow, OAuthPolicyData policyData) {
        OAuth2AuthenticationData authData = policyData.data();
        // Register static OIDC clients (policies without runtime expressions).
        // This is called once at application startup by the SDK, not per HTTP request.
        // The processedWorkflows Set in the registrar ensures each workflow is processed only once.
        // Dynamic clients (with expressions) are skipped here and registered lazily by the provider.
        workflowRegistrar.registerStaticOidcClientsFor(workflow);
        return new OidcClientAuthProvider(
                authName,
                clientRegistry,
                configResolver,
                workflowRegistrar,
                resolveEndpointKey(app, authData),
                resolveDynamicGrantParams(app, authData));
    }

    private static String authPolicyName(ReferenceableAuthenticationPolicy auth) {
        return auth != null && auth.getAuthenticationPolicyReference() != null
                ? auth.getAuthenticationPolicyReference().getUse()
                : null;
    }
}
