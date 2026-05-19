package io.quarkiverse.flow.persistence.common;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceWriter;

/**
 * Wrapper around {@link PersistenceInstanceWriter} that filters out excluded workflows from persistence.
 * <p>
 * Workflows listed in the exclusion set will execute entirely in-memory and will not be persisted.
 */
public class FilteredPersistenceWriter implements PersistenceInstanceWriter {

    private static final Logger LOG = LoggerFactory.getLogger(FilteredPersistenceWriter.class);

    private final PersistenceInstanceWriter delegate;
    private final Collection<WorkflowDefinitionId> excludedWorkflows;

    public FilteredPersistenceWriter(PersistenceInstanceWriter delegate, Collection<WorkflowDefinitionId> excludedWorkflows) {
        this.delegate = delegate;
        this.excludedWorkflows = excludedWorkflows;
        if (!this.excludedWorkflows.isEmpty()) {
            LOG.info("Persistence filtering enabled. Excluded workflows: {}", this.excludedWorkflows);
        }
    }

    private boolean isExcluded(WorkflowContextData workflowContext) {
        WorkflowDefinitionId workflowId = workflowContext.definition().id();
        if (excludedWorkflows.contains(workflowContext.definition().id())) {
            LOG.debug("Skipping persistence for excluded workflow: {}", workflowId);
            return true;
        }
        return false;
    }

    @Override
    public CompletableFuture<Void> started(WorkflowContextData workflowContext) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.started(workflowContext);
    }

    @Override
    public CompletableFuture<Void> completed(WorkflowContextData workflowContext) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.completed(workflowContext);
    }

    @Override
    public CompletableFuture<Void> failed(WorkflowContextData workflowContext, Throwable ex) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.failed(workflowContext, ex);
    }

    @Override
    public CompletableFuture<Void> aborted(WorkflowContextData workflowContext) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.aborted(workflowContext);
    }

    @Override
    public CompletableFuture<Void> suspended(WorkflowContextData workflowContext) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.suspended(workflowContext);
    }

    @Override
    public CompletableFuture<Void> resumed(WorkflowContextData workflowContext) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.resumed(workflowContext);
    }

    @Override
    public CompletableFuture<Void> taskRetried(WorkflowContextData workflowContext, TaskContextData taskContext) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.taskRetried(workflowContext, taskContext);
    }

    @Override
    public CompletableFuture<Void> taskStarted(WorkflowContextData workflowContext, TaskContextData taskContext) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.taskStarted(workflowContext, taskContext);
    }

    @Override
    public CompletableFuture<Void> taskCompleted(WorkflowContextData workflowContext, TaskContextData taskContext) {
        if (isExcluded(workflowContext)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.taskCompleted(workflowContext, taskContext);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}