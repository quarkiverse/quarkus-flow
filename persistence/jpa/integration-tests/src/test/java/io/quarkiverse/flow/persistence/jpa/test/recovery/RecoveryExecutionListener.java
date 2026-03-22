package io.quarkiverse.flow.persistence.jpa.test.recovery;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;

@ApplicationScoped
public class RecoveryExecutionListener implements WorkflowExecutionListener {

    @ConfigProperty(name = "recovery.phase", defaultValue = "default")
    String phase;

    @Override
    public void onTaskStarted(TaskStartedEvent event) {
        if (isTargetWorkflow(event.taskContext().taskName(), event.workflowContext().definition().id().name())) {
            RecoveryTestState.recordTaskStarted(phase, event.taskContext().taskName());
        }
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (isTargetWorkflow(event.taskContext().taskName(), event.workflowContext().definition().id().name())) {
            RecoveryTestState.recordTaskCompleted(phase, event.taskContext().taskName());
        }
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (RecoveryTestConstants.WORKFLOW_NAME.equals(event.workflowContext().definition().id().name())) {
            RecoveryTestState.recordWorkflowCompleted(phase);
        }
    }

    private boolean isTargetWorkflow(String taskName, String workflowName) {
        return taskName != null && RecoveryTestConstants.WORKFLOW_NAME.equals(workflowName);
    }
}
