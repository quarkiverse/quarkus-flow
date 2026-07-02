package io.quarkiverse.flow.oidc;

import java.util.Optional;

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
 * An {@link AuthProviderFactory} that routes OAuth2/OIDC token negotiation through a Quarkus
 * {@link io.quarkus.oidc.client.OidcClient}.
 *
 * <p>
 * For inline OAuth2/OIDC policies it returns an {@link OidcClientAuthProvider}. Every other case (basic, bearer, digest, and
 * secret-based OAuth2/OIDC policies that this extension does not yet handle) is delegated to the SDK's
 * {@link DefaultAuthProviderFactory} so existing behavior is preserved.
 */
public class OidcAuthProviderFactory implements AuthProviderFactory {

    private final AuthProviderFactory delegate = DefaultAuthProviderFactory.factory();
    private final OidcClientFactory clientFactory;

    public OidcAuthProviderFactory(OidcClientFactory clientFactory) {
        this.clientFactory = clientFactory;
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
        return OAuth2Policy.from(union(definition, auth))
                .map(policy -> new OidcClientAuthProvider(definition.application(), policy, clientFactory));
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
