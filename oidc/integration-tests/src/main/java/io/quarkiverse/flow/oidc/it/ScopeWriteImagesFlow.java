package io.quarkiverse.flow.oidc.it;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ScopeWriteImagesFlow extends ScopedImagesFlow {

    @Override
    protected String workflowName() {
        return "scope-write-it";
    }

    @Override
    protected String scope() {
        return "scope-write";
    }
}
