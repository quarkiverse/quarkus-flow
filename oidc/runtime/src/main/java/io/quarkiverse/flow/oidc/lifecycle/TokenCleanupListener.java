package io.quarkiverse.flow.oidc.lifecycle;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.oidc.cache.TokenCacheRepository;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;

/**
 * Unlinks a workflow instance from its cached tokens when it terminates (completed/failed/cancelled), so
 * orphaned tokens are evicted.
 */
@ApplicationScoped
public class TokenCleanupListener implements WorkflowExecutionListener {

    @Inject
    TokenCacheRepository cache;

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
        cleanup(ev.workflowContext().instanceData().id());
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent ev) {
        cleanup(ev.workflowContext().instanceData().id());
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent ev) {
        cleanup(ev.workflowContext().instanceData().id());
    }

    private void cleanup(String instanceId) {
        if (instanceId != null) {
            cache.unlinkInstance(instanceId);
        }
    }
}
