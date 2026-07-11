package io.quarkiverse.flow.oidc;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.oidc.client.OidcClient;
import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.EndpointConfiguration;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.ReferenceableAuthenticationPolicy;
import io.serverlessworkflow.api.types.Use;
import io.serverlessworkflow.api.types.UseAuthentications;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.auth.AuthProvider;
import io.serverlessworkflow.impl.auth.AuthProviderFactory;
import io.serverlessworkflow.impl.auth.DefaultAuthProviderFactory;

/**
 * An {@link AuthProviderFactory} that routes OAuth2/OIDC token negotiation through a Quarkus
 * {@link io.quarkus.oidc.client.OidcClient}.
 *
 * <p>
 * For inline OAuth2/OIDC policies it returns an {@link OidcClientAuthProvider}. Every other case (basic, bearer, digest, and
 * secret-based OAuth2/OIDC policies that this extension does not yet handle) is delegated to the SDK's
 * {@link DefaultAuthProviderFactory} so existing behavior is preserved.
 */
public class OidcAuthProviderFactory implements AuthProviderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(OidcAuthProviderFactory.class);

    private final AuthProviderFactory delegate = DefaultAuthProviderFactory.factory();
    private final OidcClientRegistry clientRegistry;
    private final FlowOidcConfig config;
    private final OidcConfigResolver configResolver;
    private final OidcWorkflowRegistrationListener workflowRegistration;

    public OidcAuthProviderFactory(OidcClientRegistry clientRegistry, FlowOidcConfig config,
            OidcConfigResolver configResolver, OidcWorkflowRegistrationListener workflowRegistration) {
        this.clientRegistry = clientRegistry;
        this.config = config;
        this.configResolver = configResolver;
        this.workflowRegistration = workflowRegistration;
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

        // 1. Process workflow to create/register all OIDC clients
        //    (listener handles all routing logic: task-level, workflow-level, named auth)
        workflowRegistration.processWorkflow(definition.workflow());

        // 2. Match by endpoint configuration
        //    (listener already registered clients with correct routing applied)
        final EndpointKey endpointKey = EndpointKey.from(policyUnion);
        if (endpointKey == null)
            return Optional.empty();

        LOG.debug("Factory querying for EndpointKey: {}", endpointKey);
        final OidcClient matchedClient = clientRegistry.getByEndpoint(endpointKey);

        if (matchedClient == null) {
            LOG.debug("No OIDC client found matching endpoint config: {}", endpointKey);
            return Optional.empty(); // Delegate to SDK's default OAuth2 provider
        }

        LOG.debug("Matched OIDC client for endpoint config: {}", endpointKey);

        // Extract auth data for dynamic grant parameters (token exchange)
        final OAuth2AuthenticationData authData = TokenAuthPolicy.tokenAuthData(policyUnion);

        return Optional.of(new OidcClientAuthProvider(definition.application(), authData, matchedClient,
                config.connectionTimeout()));
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
