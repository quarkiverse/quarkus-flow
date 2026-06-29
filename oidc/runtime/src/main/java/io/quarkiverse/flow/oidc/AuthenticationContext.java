package io.quarkiverse.flow.oidc;

import java.util.Optional;

import io.quarkiverse.flow.oidc.config.FlowOidcConfig.AuthSchemeConfig;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;

/**
 * Everything a {@link AuthenticationProvider} needs to produce a token for one HTTP call: the resolved
 * scheme name and its configuration, the executing workflow/task contexts, and the caller's subject token
 * (already extracted once by {@link AuthenticationRegistry}; empty when none is available).
 */
public record AuthenticationContext(
        String schemeName,
        AuthSchemeConfig schemeConfig,
        WorkflowContext workflowContext,
        TaskContext taskContext,
        Optional<String> subjectToken) {

    /**
     * Creates a context before the subject token has been resolved (used by the request decorator).
     * {@link AuthenticationRegistry} later derives the enriched copy via {@link #withSubjectToken(String)}.
     */
    public AuthenticationContext(String schemeName, AuthSchemeConfig schemeConfig,
            WorkflowContext workflowContext, TaskContext taskContext) {
        this(schemeName, schemeConfig, workflowContext, taskContext, Optional.empty());
    }

    /**
     * Returns a copy of this context carrying the given subject token ({@code null} when none is available).
     */
    public AuthenticationContext withSubjectToken(String subjectToken) {
        return new AuthenticationContext(schemeName, schemeConfig, workflowContext, taskContext,
                Optional.ofNullable(subjectToken));
    }

    public String instanceId() {
        return workflowContext.instance().id();
    }
}
