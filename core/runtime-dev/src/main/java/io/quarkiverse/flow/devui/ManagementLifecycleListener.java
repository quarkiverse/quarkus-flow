package io.quarkiverse.flow.devui;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                    ev.workflowContext().instanceData().status(),
                    now,
                    ev.workflowContext().instanceData().input().asJavaObject());

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
                existing.recordTaskFailure(ev.taskContext().taskName(), now, ev.cause());
                store.saveOrUpdate(existing);
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
                existing.recordCompletion(now, ev.output().asJavaObject());
                store.saveOrUpdate(existing);
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
                existing.recordFailure(now, ev.cause());
                store.saveOrUpdate(existing);
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
            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                existing.recordCancellation(ev.eventDate().toInstant());
                store.saveOrUpdate(existing);
            });
        } catch (Exception e) {
            log.error("Error handling workflow cancelled event", e);
        }
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                existing.recordSuspension(ev.eventDate().toInstant());
                store.saveOrUpdate(existing);
            });
        } catch (Exception e) {
            log.error("Error handling workflow suspended event", e);
        }
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                existing.recordResumption(ev.eventDate().toInstant());
                store.saveOrUpdate(existing);
            });
        } catch (Exception e) {
            log.error("Error handling workflow resumed event", e);
        }
    }

    @Override
    public void onWorkflowStatusChanged(WorkflowStatusEvent ev) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                existing.recordStatusChange(ev.workflowContext().instanceData().status().name(), ev.eventDate().toInstant());
                store.saveOrUpdate(existing);
            });
        } catch (Exception e) {
            log.error("Error handling workflow status changed event", e);
        }
    }

    @Override
    public void close() {
        // no-op
    }

    private void updateInstanceWithTaskEvent(TaskEvent ev, String eventType) {
        try {
            String instanceId = ev.workflowContext().instanceData().id();
            Optional.ofNullable(store.findByInstanceId(instanceId)).ifPresent(existing -> {
                existing.recordTaskEvent(eventType, ev.taskContext().taskName(), ev.eventDate().toInstant());
                store.saveOrUpdate(existing);
            });
        } catch (Exception e) {
            log.error("Error handling {} event", eventType, e);
        }
    }
}
