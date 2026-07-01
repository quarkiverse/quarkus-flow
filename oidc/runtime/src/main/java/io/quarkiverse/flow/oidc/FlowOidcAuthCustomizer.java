package io.quarkiverse.flow.oidc;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.oidc.client.OidcClients;
import io.serverlessworkflow.impl.WorkflowApplication;

/**
 * Plugs the {@link OidcAuthProviderFactory} into the workflow application so OAuth2/OIDC token negotiation is delegated to
 * {@code quarkus-oidc-client}.
 */
@ApplicationScoped
public class FlowOidcAuthCustomizer implements WorkflowApplicationBuilderCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(FlowOidcAuthCustomizer.class);

    @Inject
    OidcClients oidcClients;

    @Inject
    FlowOidcConfig config;

    private volatile OidcClientFactory clientFactory;

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        if (!config.enabled()) {
            LOG.info("Flow OIDC: disabled; SDK default OAuth2/OIDC token negotiation in effect.");
            return;
        }
        this.clientFactory = new OidcClientFactory(oidcClients);
        builder.withAuthProviderFactory(new OidcAuthProviderFactory(clientFactory, config.requestTimeout()));
        LOG.info("Flow OIDC: OAuth2/OIDC token negotiation delegated to quarkus-oidc-client.");
    }

    @PreDestroy
    void close() {
        final OidcClientFactory factory = this.clientFactory;
        if (factory != null) {
            factory.close();
        }
    }
}
