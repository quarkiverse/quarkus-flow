package io.quarkiverse.flow.langchain4j.workflow;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.withInstanceId;

import java.util.List;

import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

public abstract class ParallelAgenticFlow extends AgenticFlow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow()
                .document(buildDocument())
                .input(inputSchema())
                .tasks(tasks -> tasks.fork("parallel", fork -> {
                    List<String> taskNames = subAgentTaskNames();
                    for (int i = 0; i < taskNames.size(); i++) {
                        final int index = i; // capture for lambda
                        String branchName = taskNames.get(i) + "-" + i;
                        fork.branches(withInstanceId(branchName,
                                (String instanceId, DefaultAgenticScope scope) -> {
                                    executeAgent(scope, index);
                                    return null;
                                },
                                DefaultAgenticScope.class));
                    }
                }))
                .build();
    }

}
