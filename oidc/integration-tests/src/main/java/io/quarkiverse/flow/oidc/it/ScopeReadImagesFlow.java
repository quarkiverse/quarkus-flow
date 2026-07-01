package io.quarkiverse.flow.oidc.it;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ScopeReadImagesFlow extends ScopedImagesFlow {

    @Override
    protected String workflowName() {
        return "scope-read-it";
    }

    @Override
    protected String scope() {
        return "scope-read";
    }
}
