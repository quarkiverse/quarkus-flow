package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ConditionalWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("conditional-routing")
                .tasks(
                        // 1. Evaluate the condition and branch
                        switchWhenOrElse(".score >= 80", "approveTask", "rejectTask"),

                        // 2. Branch A: Score is 80 or higher
                        post("approveTask", "", "http://localhost:8089/approve")
                                .then(FlowDirectiveEnum.END), // equals to break; in switch cases

                        // 3. Branch B: Score is below 80
                        post("rejectTask", "", "http://localhost:8089/reject"))
                .build();
    }
}
