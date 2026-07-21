package io.quarkiverse.flow.langchain4j.workflow.flow;

import java.util.List;

import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowContextData;

public abstract class SequentialAgenticFlow extends AgenticFlow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow()
                .document(buildDocument())
                .input(inputSchema())
                .tasks(tasks -> {
                    List<String> taskNames = subAgentTaskNames();
                    for (int i = 0; i < taskNames.size(); i++) {
                        final int index = i; // capture for lambda
                        String taskName = taskNames.get(i) + "-" + i;
                        tasks.function(
                                taskName,
                                fn -> fn.function(
                                        (DefaultAgenticScope scope, WorkflowContextData ctx) -> executeAgent(
                                                ctx.instanceData().id(), scope,
                                                index),
                                        DefaultAgenticScope.class)
                                        .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
                    }
                })
                .build();
    }
}
