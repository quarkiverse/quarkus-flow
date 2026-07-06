package io.quarkiverse.flow.oidc.it;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EndpointAImagesFlow extends DistinctEndpointImagesFlow {

    @Override
    protected String workflowName() {
        return "endpoint-a-it";
    }

    @Override
    protected String tokenPath() {
        return "/oauth2/a/token";
    }
}
