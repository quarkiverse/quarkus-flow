package io.quarkiverse.flow.opentelemetry.it;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

/**
 * A plain {@code function(..)} task that calls two different leaf {@code @Agent} beans. The task span is typed with the
 * first agent invoked, and a {@code gen_ai.invoke_agent} span event is recorded for each call.
 */
@ApplicationScoped
public class TwoLeafAgentsCallFlow extends Flow {

    public static final String NAME = "two-leaf-agents-call";
    public static final String TASK_NAME = "triageAndEscalateTask";

    @Inject
    TriageLeafAgent triageAgent;

    @Inject
    EscalationLeafAgent escalationAgent;

    @Override
    public Workflow descriptor() {
        return workflow(NAME)
                .tasks(function(TASK_NAME, this::triageAndEscalate, String.class))
                .build();
    }

    private String triageAndEscalate(String email) {
        String triage = triageAgent.triage(email);
        escalationAgent.escalate(email);
        return triage;
    }
}
