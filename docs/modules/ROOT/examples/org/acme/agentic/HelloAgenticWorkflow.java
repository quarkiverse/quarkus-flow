package org.acme.agentic;

import static io.quarkiverse.flow.dsl.FlowDSL.agent;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;

/*
 * =========================================================
 * Workflow using both agents (included via: [tag=workflow])
 * =======================================================
 */
// tag::workflow[]
@ApplicationScoped
public class HelloAgenticWorkflow extends Flow {

    @Inject
    org.acme.agentic.DrafterAgent drafterAgent;
    @Inject
    org.acme.agentic.CriticAgent criticAgent;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("hello-agentic")
                .tasks(
                        // Build a single brief string from topic + notes and feed it to the drafter
                        // (jq-style expression produces a String)
                        agent("draftAgent", drafterAgent::draft, String.class)
                                .inputFrom("\"Topic: \" + .topic +\"\\nNotes: \"+ .notes")
                                .exportAs("."), // expose the whole draft text to the next step

                        // Critic evaluates the draft and we persist a normalized review state
                        agent("criticAgent", criticAgent::critique, String.class)
                                .outputAs("{ reviewRaw: ., needsRevision: (. | tostring | startswith(\"NEEDS_REVISION:\")) }"),

                        // If needsRevision == true → loop back to draftAgent; else END
                        switchWhenOrElse(
                                ".needsRevision",
                                "draftAgent",
                                FlowDirectiveEnum.END))
                .build();
    }
}
// end::workflow[]
