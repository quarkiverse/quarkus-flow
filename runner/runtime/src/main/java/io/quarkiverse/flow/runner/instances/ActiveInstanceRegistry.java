package io.quarkiverse.flow.runner.instances;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.runner.model.InstanceSnapshot;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

/**
 * Tracks in-memory active workflow instances using the SDK lifecycle listener.
 *
 * <p>
 * Instances are added on {@code onWorkflowStarted} and removed when they reach a terminal state
 * (completed, failed, or cancelled). Status updates (e.g. SUSPENDED → RUNNING) are reflected via
 * {@code onWorkflowStatusChanged}, {@code onWorkflowSuspended}, and {@code onWorkflowResumed}.
 *
 * <p>
 * This registry is the source of truth for {@code GET /q/flow/instances}, enabling a rebalancer
 * service to distinguish between instances that are actively being processed vs. those persisted in
 * the DB but not running on this pod.
 */
@ApplicationScoped
public class ActiveInstanceRegistry implements WorkflowExecutionListener {

    private final Map<String, InstanceSnapshot> activeInstances = new ConcurrentHashMap<>();

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
        String instanceId = ev.workflowContext().instanceData().id();
        if (instanceId == null) {
            return;
        }
        InstanceSnapshot snapshot = new InstanceSnapshot(
                instanceId,
                ev.workflowContext().definition().workflow().getDocument().getName(),
                ev.workflowContext().definition().workflow().getDocument().getNamespace(),
                ev.workflowContext().definition().workflow().getDocument().getVersion(),
                ev.workflowContext().instanceData().status(),
                ev.workflowContext().instanceData().startedAt());
        activeInstances.put(instanceId, snapshot);
    }

    @Override
    public void onWorkflowStatusChanged(WorkflowStatusEvent ev) {
        updateStatus(ev.workflowContext().instanceData().id(), ev.workflowContext().instanceData().status());
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent ev) {
        updateStatus(ev.workflowContext().instanceData().id(), ev.workflowContext().instanceData().status());
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent ev) {
        updateStatus(ev.workflowContext().instanceData().id(), ev.workflowContext().instanceData().status());
    }

    private void updateStatus(String instanceId, WorkflowStatus status) {
        if (instanceId == null) {
            return;
        }
        activeInstances.computeIfPresent(instanceId, (id, existing) -> new InstanceSnapshot(
                existing.instanceId(),
                existing.workflowName(),
                existing.workflowNamespace(),
                existing.workflowVersion(),
                status,
                existing.startedAt()));
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
        remove(ev);
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent ev) {
        remove(ev);
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent ev) {
        remove(ev);
    }

    private void remove(WorkflowEvent ev) {
        String instanceId = ev.workflowContext().instanceData().id();
        if (instanceId != null) {
            activeInstances.remove(instanceId);
        }
    }

    public Collection<InstanceSnapshot> activeInstances() {
        return Collections.unmodifiableCollection(activeInstances.values());
    }
}
