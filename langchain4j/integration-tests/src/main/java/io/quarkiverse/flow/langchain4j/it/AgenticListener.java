package io.quarkiverse.flow.langchain4j.it;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

@ApplicationScoped
public class AgenticListener implements WorkflowExecutionListener {

    public static final List<TaskStartedEvent> TASK_STARTED_EVENTS = new CopyOnWriteArrayList<>();
    public static final List<WorkflowCompletedEvent> WORKFLOW_COMPLETED_EVENTS = new CopyOnWriteArrayList<>();
    public static final List<WorkflowStartedEvent> WORKFLOW_STARTED_EVENTS = new CopyOnWriteArrayList<>();

    public static void clearAll() {
        TASK_STARTED_EVENTS.clear();
        WORKFLOW_COMPLETED_EVENTS.clear();
        WORKFLOW_STARTED_EVENTS.clear();
    }

    @Override
    public void onTaskStarted(TaskStartedEvent ev) {
        TASK_STARTED_EVENTS.add(ev);
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
        WORKFLOW_STARTED_EVENTS.add(ev);
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
        WORKFLOW_COMPLETED_EVENTS.add(ev);
    }
}
