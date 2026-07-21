package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;

@ApplicationScoped
public class TaskContextWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("task-context-workflow")
                .tasks(
                        withFilter("taskAudit",
                                (ExampleEvent payload,
                                        WorkflowContextData workflowContextData,
                                        TaskContextData taskContextData) -> {
                                    // Access the task context
                                    System.out.println("Local Task Name: " + taskContextData.taskName());
                                    System.out.println("Processing Message: " + payload.eventName());

                                    return "Audited [" + payload.eventName() + "] via task: " + taskContextData.taskName();
                                }, ExampleEvent.class))
                .build();
    }
}
