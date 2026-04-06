package org.acme.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FlowCustomListener implements WorkflowExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(FlowCustomListener.class);

    @Override
    public void onTaskCompleted(TaskCompletedEvent ev) {
        if (ev.taskContext().taskName().equals("inc")) {
            logger.info("Workflow {} incremented count to {}", ev.workflowContext().instanceData().id(),
                    ev.taskContext().output().asMap().map(m -> m.get("count")).orElseThrow());
        }
    }

}
