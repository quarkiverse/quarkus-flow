package io.quarkiverse.flow.langchain4j.workflow.flow;

import static io.quarkiverse.flow.dsl.FlowDSL.withInstanceId;

import java.util.List;

import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

public abstract class ParallelAgenticFlow extends AgenticFlow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow()
                .document(buildDocument())
                .input(inputSchema())
                .tasks(tasks -> tasks.fork("parallel", fork -> {
                    List<String> taskNames = subAgentTaskNames();
                    for (int i = 0; i < taskNames.size(); i++) {
                        final int index = i; // capture for lambda
                        String branchName = taskNames.get(i) + "-" + i;
                        fork.branches(withInstanceId(branchName,
                                (String instanceId, DefaultAgenticScope scope) -> {
                                    executeAgent(instanceId, scope, index);
                                    return null;
                                },
                                DefaultAgenticScope.class));
                    }
                }))
                .build();
    }

}
