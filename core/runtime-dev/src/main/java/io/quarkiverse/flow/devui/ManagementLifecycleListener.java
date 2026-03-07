package io.quarkiverse.flow.devui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.FlowInstance;
import io.quarkiverse.flow.internal.FlowInstance.LifecycleEventSummary;
import io.quarkiverse.flow.internal.WorkflowInstanceStore;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

@ApplicationScoped
public class ManagementLifecycleListener implements WorkflowExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ManagementLifecycleListener.class);

    private final WorkflowInstanceStore store;

    public ManagementLifecycleListener(WorkflowInstanceStore store) {
        this.store = store;
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            FlowInstance flowInstance = new FlowInstance(
                    instanceId,
                    ev.workflowContext().definition().workflow().getDocument().getNamespace(),
                    ev.workflowContext().definition().workflow().getDocument().getName(),
                    ev.workflowContext().definition().workflow().getDocument().getVersion(),
                    ev.workflowContext().instanceData().status().name(),
                    now,
                    now,
                    null,
                    null,
                    null,
                    ev.workflowContext().instanceData().input().asJavaObject(),
                    null,
                    new ArrayList<>(List.of(
                            new LifecycleEventSummary("workflow.started", null, now, null))));

            store.saveOrUpdate(flowInstance);
            log.debug("Workflow started: {}", instanceId);
        } catch (Exception e) {
            log.error("Error handling workflow started event", e);
        }
    }

    @Override
    public void onTaskStarted(TaskStartedEvent ev) {
        updateInstanceWithTaskEvent(ev, "task.started");
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent ev) {
        updateInstanceWithTaskEvent(ev, "task.completed");
    }

    @Override
    public void onTaskFailed(TaskFailedEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                List<LifecycleEventSummary> history = new ArrayList<>(existing.history());
                String errorMsg = ev.cause() != null ? ev.cause().getMessage() : null;

                history.add(new LifecycleEventSummary("task.failed", ev.taskContext().taskName(), now, errorMsg));

                FlowInstance updated = new FlowInstance(
                        existing.instanceId(),
                        existing.workflowNamespace(),
                        existing.workflowName(),
                        existing.workflowVersion(),
                        ev.workflowContext().instanceData().status().name(),
                        existing.startTime(),
                        now,
                        existing.endTime(),
                        ev.cause() != null ? ev.cause().getClass().getSimpleName() : existing.errorCode(),
                        errorMsg != null ? truncate(errorMsg) : existing.errorMessage(),
                        existing.input(),
                        existing.output(),
                        history);

                store.saveOrUpdate(updated);
            });
        } catch (Exception e) {
            log.error("Error handling task failed event", e);
        }
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent ev) {
        updateInstanceWithTaskEvent(ev, "task.cancelled");
    }

    @Override
    public void onTaskSuspended(TaskSuspendedEvent ev) {
        updateInstanceWithTaskEvent(ev, "task.suspended");
    }

    @Override
    public void onTaskResumed(TaskResumedEvent ev) {
        updateInstanceWithTaskEvent(ev, "task.resumed");
    }

    @Override
    public void onTaskRetried(TaskRetriedEvent ev) {
        updateInstanceWithTaskEvent(ev, "task.retried");
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                List<LifecycleEventSummary> history = new ArrayList<>(existing.history());
                history.add(new LifecycleEventSummary("workflow.completed", null, now, null));

                FlowInstance updated = new FlowInstance(
                        existing.instanceId(),
                        existing.workflowNamespace(),
                        existing.workflowName(),
                        existing.workflowVersion(),
                        ev.workflowContext().instanceData().status().name(),
                        existing.startTime(),
                        now,
                        now,
                        existing.errorCode(),
                        existing.errorMessage(),
                        existing.input(),
                        ev.output().asJavaObject(),
                        history);

                store.saveOrUpdate(updated);
                log.debug("Workflow completed: {}", instanceId);
            });
        } catch (Exception e) {
            log.error("Error handling workflow completed event", e);
        }
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                List<LifecycleEventSummary> history = new ArrayList<>(existing.history());
                String errorMsg = ev.cause() != null ? ev.cause().getMessage() : null;
                history.add(new LifecycleEventSummary("workflow.failed", null, now, errorMsg));

                FlowInstance updated = new FlowInstance(
                        existing.instanceId(),
                        existing.workflowNamespace(),
                        existing.workflowName(),
                        existing.workflowVersion(),
                        WorkflowStatus.FAULTED.name(),
                        existing.startTime(),
                        now,
                        now,
                        ev.cause() != null ? ev.cause().getClass().getSimpleName() : null,
                        errorMsg != null ? truncate(errorMsg) : null,
                        existing.input(),
                        existing.output(),
                        history);

                store.saveOrUpdate(updated);
                log.debug("Workflow failed: {}", instanceId);
            });
        } catch (Exception e) {
            log.error("Error handling workflow failed event", e);
        }
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                List<LifecycleEventSummary> history = new ArrayList<>(existing.history());
                history.add(new LifecycleEventSummary("workflow.cancelled", null, now, null));

                FlowInstance updated = new FlowInstance(
                        existing.instanceId(),
                        existing.workflowNamespace(),
                        existing.workflowName(),
                        existing.workflowVersion(),
                        WorkflowStatus.CANCELLED.name(),
                        existing.startTime(),
                        now,
                        now,
                        existing.errorCode(),
                        existing.errorMessage(),
                        existing.input(),
                        existing.output(),
                        history);

                store.saveOrUpdate(updated);
            });
        } catch (Exception e) {
            log.error("Error handling workflow cancelled event", e);
        }
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                List<LifecycleEventSummary> history = new ArrayList<>(existing.history());
                history.add(new LifecycleEventSummary("workflow.suspended", null, now, null));

                FlowInstance updated = new FlowInstance(
                        existing.instanceId(),
                        existing.workflowNamespace(),
                        existing.workflowName(),
                        existing.workflowVersion(),
                        WorkflowStatus.SUSPENDED.name(),
                        existing.startTime(),
                        now,
                        existing.endTime(),
                        existing.errorCode(),
                        existing.errorMessage(),
                        existing.input(),
                        existing.output(),
                        history);

                store.saveOrUpdate(updated);
            });
        } catch (Exception e) {
            log.error("Error handling workflow suspended event", e);
        }
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                List<LifecycleEventSummary> history = new ArrayList<>(existing.history());
                history.add(new LifecycleEventSummary("workflow.resumed", null, now, null));

                FlowInstance updated = new FlowInstance(
                        existing.instanceId(),
                        existing.workflowNamespace(),
                        existing.workflowName(),
                        existing.workflowVersion(),
                        WorkflowStatus.RUNNING.name(),
                        existing.startTime(),
                        now,
                        existing.endTime(),
                        existing.errorCode(),
                        existing.errorMessage(),
                        existing.input(),
                        existing.output(),
                        history);

                store.saveOrUpdate(updated);
            });
        } catch (Exception e) {
            log.error("Error handling workflow resumed event", e);
        }
    }

    @Override
    public void onWorkflowStatusChanged(WorkflowStatusEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                List<LifecycleEventSummary> history = new ArrayList<>(existing.history());
                history.add(new LifecycleEventSummary("workflow.status.changed", null, now,
                        ev.workflowContext().instanceData().status().name()));

                FlowInstance updated = new FlowInstance(
                        existing.instanceId(),
                        existing.workflowNamespace(),
                        existing.workflowName(),
                        existing.workflowVersion(),
                        ev.workflowContext().instanceData().status().name(),
                        existing.startTime(),
                        now,
                        existing.endTime(),
                        existing.errorCode(),
                        existing.errorMessage(),
                        existing.input(),
                        existing.output(),
                        history);

                store.saveOrUpdate(updated);
            });
        } catch (Exception e) {
            log.error("Error handling workflow status changed event", e);
        }
    }

    @Override
    public void close() {
        // No resources to close
    }

    private void updateInstanceWithTaskEvent(TaskEvent ev, String eventType) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Instant now = ev.eventDate().toInstant();

            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                List<LifecycleEventSummary> history = new ArrayList<>(existing.history());
                history.add(
                        new LifecycleEventSummary(eventType, ev.taskContext().taskName(), ev.eventDate().toInstant(), null));

                FlowInstance updated = new FlowInstance(
                        existing.instanceId(),
                        existing.workflowNamespace(),
                        existing.workflowName(),
                        existing.workflowVersion(),
                        ev.workflowContext().instanceData().status().name(),
                        existing.startTime(),
                        now,
                        existing.endTime(),
                        existing.errorCode(),
                        existing.errorMessage(),
                        existing.input(),
                        existing.output(),
                        history);

                store.saveOrUpdate(updated);
            });
        } catch (Exception e) {
            log.error("Error handling {} event", eventType, e);
        }
    }

    private String truncate(String str) {
        if (str == null || str.length() <= 1024) {
            return str;
        }
        return str.substring(0, 1024) + "... (truncated)";
    }
}
