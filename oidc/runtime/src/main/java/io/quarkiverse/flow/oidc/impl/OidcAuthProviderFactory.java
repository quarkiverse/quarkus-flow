package io.quarkiverse.flow.oidc.impl;

import java.util.Optional;

import io.quarkiverse.flow.oidc.registry.OidcClientRegistry;
import io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar;
import io.quarkiverse.flow.oidc.registry.OidcConfigResolver;
import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.EndpointConfiguration;
import io.serverlessworkflow.api.types.ReferenceableAuthenticationPolicy;
import io.serverlessworkflow.api.types.Use;
import io.serverlessworkflow.api.types.UseAuthentications;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.auth.AuthProvider;
import io.serverlessworkflow.impl.auth.AuthProviderFactory;
import io.serverlessworkflow.impl.auth.DefaultAuthProviderFactory;

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
public class OidcAuthProviderFactory implements AuthProviderFactory {

    private final AuthProviderFactory delegate = DefaultAuthProviderFactory.factory();
    private final OidcClientRegistry clientRegistry;
    private final RuntimeExpressionResolver expressionResolver;
    private final OidcConfigResolver configResolver;
    private final OidcClientWorkflowRegistrar workflowRegistrar;

    public OidcAuthProviderFactory(OidcClientRegistry clientRegistry,
            OidcClientWorkflowRegistrar workflowRegistrar,
            RuntimeExpressionResolver expressionResolver,
            OidcConfigResolver configResolver) {
        this.clientRegistry = clientRegistry;
        this.workflowRegistrar = workflowRegistrar;
        this.expressionResolver = expressionResolver;
        this.configResolver = configResolver;
    }

    @Override
    public Optional<AuthProvider> getAuth(WorkflowDefinition definition, EndpointConfiguration configuration) {
        if (configuration == null) {
            return delegate.getAuth(definition, null);
        }
        final Optional<AuthProvider> mine = build(definition, configuration.getAuthentication());
        return mine.isPresent() ? mine : delegate.getAuth(definition, configuration);
    }

    @Override
    public Optional<AuthProvider> getAuth(WorkflowDefinition definition, ReferenceableAuthenticationPolicy auth,
            String method) {
        final Optional<AuthProvider> mine = build(definition, auth);
        return mine.isPresent() ? mine : delegate.getAuth(definition, auth, method);
    }

    private Optional<AuthProvider> build(WorkflowDefinition definition, ReferenceableAuthenticationPolicy auth) {
        final AuthenticationPolicyUnion policyUnion = union(definition, auth);

        if (policyUnion == null)
            return Optional.empty();

        // Check if this is an OAuth2 or OIDC policy - delegate others to SDK
        if (policyUnion.getOAuth2AuthenticationPolicy() == null &&
                policyUnion.getOpenIdConnectAuthenticationPolicy() == null) {
            return Optional.empty(); // Not OAuth2/OIDC - delegate to SDK
        }

        // Register static OIDC clients (policies without runtime expressions).
        // This is called once at application startup by the SDK, not per HTTP request.
        // The processedWorkflows Set in the registrar ensures each workflow is processed only once.
        // Dynamic clients (with expressions) are skipped here and registered lazily by the provider.
        workflowRegistrar.registerStaticOidcClientsFor(definition.workflow());

        return Optional.of(new OidcClientAuthProvider(
                policyUnion.getOAuth2AuthenticationPolicy() == null
                        ? policyUnion.getOpenIdConnectAuthenticationPolicy().getOidc()
                                .getOpenIdConnectAuthenticationProperties()
                        : policyUnion.getOAuth2AuthenticationPolicy().getOauth2().getOAuth2ConnectAuthenticationProperties(),
                authPolicyName(auth),
                clientRegistry,
                configResolver,
                workflowRegistrar,
                expressionResolver));
    }

    private String authPolicyName(ReferenceableAuthenticationPolicy auth) {
        if (auth != null && auth.getAuthenticationPolicyReference() != null) {
            return auth.getAuthenticationPolicyReference().getUse();
        }
        return null;
    }

    private AuthenticationPolicyUnion union(WorkflowDefinition definition, ReferenceableAuthenticationPolicy auth) {
        if (auth == null) {
            return null;
        }
        if (auth.getAuthenticationPolicyReference() != null) {
            final String use = auth.getAuthenticationPolicyReference().getUse();
            final Use useInfo = definition.workflow().getUse();
            if (useInfo == null || useInfo.getAuthentications() == null) {
                return null;
            }
            final UseAuthentications authentications = useInfo.getAuthentications();
            return authentications.getAdditionalProperties().get(use);
        }
        return auth.getAuthenticationPolicy();
    }
}
