package io.quarkiverse.flow.oidc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.oidc.impl.OidcAuthProviderFactory;
import io.quarkiverse.flow.oidc.impl.RuntimeExpressionResolver;
import io.quarkiverse.flow.oidc.registry.OidcClientRegistry;
import io.quarkiverse.flow.oidc.registry.OidcClientWorkflowRegistrar;
import io.quarkiverse.flow.oidc.registry.OidcConfigResolver;
import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.serverlessworkflow.impl.WorkflowApplication;

/**
 * Plugs the {@link OidcAuthProviderFactory} into the workflow application so OAuth2/OIDC token negotiation is delegated to
 * {@code quarkus-oidc-client}.
 */
@ApplicationScoped
public class FlowOidcAuthCustomizer implements WorkflowApplicationBuilderCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(FlowOidcAuthCustomizer.class);

    @Inject
    FlowOidcConfig flowOidcConfig;

    @Inject
    OidcClientRegistry clientRegistry;

    @Inject
    OidcClientWorkflowRegistrar workflowRegistrar;

    @Inject
    RuntimeExpressionResolver expressionResolver;

    @Inject
    OidcConfigResolver configResolver;

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        if (!flowOidcConfig.enabled()) {
            LOG.info("Flow OIDC: disabled; SDK default OAuth2/OIDC token negotiation in effect.");
            return;
        }
        OidcAuthProviderFactory factory = new OidcAuthProviderFactory(clientRegistry, workflowRegistrar, expressionResolver, configResolver);
        LOG.info("Flow OIDC: Registering OidcAuthProviderFactory: {}", factory);
        builder.withAuthProviderFactory(factory);
        LOG.info("Flow OIDC: OAuth2/OIDC token negotiation delegated to quarkus-oidc-client.");
    }
}
