package io.quarkiverse.flow.lifecycle.ce;

import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.ce.DefaultLifeCycleCloudEventFactory;
import io.serverlessworkflow.impl.lifecycle.ce.TaskCancelledCEData;
import io.serverlessworkflow.impl.lifecycle.ce.TaskCompletedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.TaskFailedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.TaskResumedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.TaskRetriedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.TaskStartedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.TaskSuspendedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowCancelledCEData;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowCompletedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowFailedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowResumedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowStartedCEData;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowStatusCEDataEvent;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowSuspendedCEData;

/**
 * Lifecycle CloudEvent factory that adds the {@code workflowApplicationId} to the data payload of every workflow
 * and task lifecycle event, so consumers can tell which application instance (e.g. which Runner pod) produced it.
 *
 * @see io.quarkiverse.flow.lifecycle.WorkflowApplicationIds
 */
public class FlowLifeCycleCloudEventFactory extends DefaultLifeCycleCloudEventFactory {

    @Override
    public WorkflowStartedCEData build(WorkflowStartedEvent event) {
        return new FlowWorkflowStartedCEData(event);
    }

    @Override
    public WorkflowCompletedCEData build(WorkflowCompletedEvent event) {
        return new FlowWorkflowCompletedCEData(event);
    }

    @Override
    public WorkflowFailedCEData build(WorkflowFailedEvent event) {
        return new FlowWorkflowFailedCEData(event);
    }

    @Override
    public WorkflowCancelledCEData build(WorkflowCancelledEvent event) {
        return new FlowWorkflowCancelledCEData(event);
    }

    @Override
    public WorkflowSuspendedCEData build(WorkflowSuspendedEvent event) {
        return new FlowWorkflowSuspendedCEData(event);
    }

    @Override
    public WorkflowResumedCEData build(WorkflowResumedEvent event) {
        return new FlowWorkflowResumedCEData(event);
    }

    @Override
    public WorkflowStatusCEDataEvent build(WorkflowStatusEvent event) {
        return new FlowWorkflowStatusCEData(event);
    }

    @Override
    public TaskStartedCEData build(TaskStartedEvent event) {
        return new FlowTaskStartedCEData(event);
    }

    @Override
    public TaskCompletedCEData build(TaskCompletedEvent event) {
        return new FlowTaskCompletedCEData(event);
    }

    @Override
    public TaskFailedCEData build(TaskFailedEvent event) {
        return new FlowTaskFailedCEData(event);
    }

    @Override
    public TaskCancelledCEData build(TaskCancelledEvent event) {
        return new FlowTaskCancelledCEData(event);
    }

    @Override
    public TaskSuspendedCEData build(TaskSuspendedEvent event) {
        return new FlowTaskSuspendedCEData(event);
    }

    @Override
    public TaskResumedCEData build(TaskResumedEvent event) {
        return new FlowTaskResumedCEData(event);
    }

    @Override
    public TaskRetriedCEData build(TaskRetriedEvent event) {
        return new FlowTaskRetriedCEData(event);
    }
}
