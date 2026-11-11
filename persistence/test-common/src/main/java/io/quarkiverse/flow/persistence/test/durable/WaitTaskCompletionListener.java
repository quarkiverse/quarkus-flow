package io.quarkiverse.flow.persistence.test.durable;

import static io.quarkiverse.flow.persistence.test.durable.RecoveryWaitWorkflow.WAIT_TASK_NAME;

import jakarta.enterprise.context.ApplicationScoped;

import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;

@ApplicationScoped
public class WaitTaskCompletionListener implements WorkflowExecutionListener {

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (WAIT_TASK_NAME.equals(event.taskContext().taskName())) {
            RecoveryWaitResource.TASK3_WAIT_TASK.incrementAndGet();
        }
    }
}
