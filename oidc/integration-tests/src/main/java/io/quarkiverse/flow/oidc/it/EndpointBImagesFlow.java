package io.quarkiverse.flow.oidc.it;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EndpointBImagesFlow extends DistinctEndpointImagesFlow {

    @Override
    protected String workflowName() {
        return "endpoint-b-it";
    }

    @Override
    protected String tokenPath() {
        return "/oauth2/b/token";
    }
}
