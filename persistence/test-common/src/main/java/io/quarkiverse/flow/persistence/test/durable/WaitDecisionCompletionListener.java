package io.quarkiverse.flow.persistence.test.durable;

import static io.quarkiverse.flow.persistence.test.durable.RecoveryWorkflow.WAIT_DECISION_TASK_NAME;

import jakarta.enterprise.context.ApplicationScoped;

import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;

@ApplicationScoped
public class WaitDecisionCompletionListener implements WorkflowExecutionListener {

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (WAIT_DECISION_TASK_NAME.equals(event.taskContext().taskName())) {
            RecoveryResource.TASK_3_WAIT_DECISION_TASK.incrementAndGet();
        }
    }
}
