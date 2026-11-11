package io.quarkiverse.flow.opentelemetry.it;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

/**
 * A plain {@code function(..)} task that calls a leaf {@code @Agent} bean directly, without the Java DSL's
 * {@code agent(..)} helper. Regression fixture for the gap described in issue #1138: runtime detection must type this
 * task's span even though it is not built with {@code agent(..)}.
 */
@ApplicationScoped
public class LeafAgentCallFlow extends Flow {

    public static final String NAME = "leaf-agent-call";
    public static final String TASK_NAME = "triageTask";

    @Inject
    TriageLeafAgent triageAgent;

    @Override
    public Workflow descriptor() {
        return workflow(NAME)
                .tasks(function(TASK_NAME, this::triage, String.class))
                .build();
    }

    private String triage(String email) {
        return triageAgent.triage(email);
    }
}
