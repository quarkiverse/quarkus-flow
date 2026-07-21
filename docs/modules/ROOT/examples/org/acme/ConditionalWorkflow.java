package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class ConditionalWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("conditional-routing")
                .tasks(
                        // 1. Evaluate the condition and branch
                        switchWhenOrElse((ScorePayload p) -> p.score() >= 80, "approveTask", "rejectTask"),

                        // 2. Branch A: Score is 80 or higher
                        post("approveTask", "", wiremockUrl + "/approve")
                                .then(FlowDirectiveEnum.END), // equals to break; in switch cases
                        // 3. Branch B: Score is below 80
                        post("rejectTask", "", wiremockUrl + "/reject"))
                .build();
    }
}
