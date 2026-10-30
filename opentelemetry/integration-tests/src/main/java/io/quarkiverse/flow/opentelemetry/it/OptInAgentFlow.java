package io.quarkiverse.flow.opentelemetry.it;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.internal.WorkflowAgentMetadata;
import io.serverlessworkflow.api.types.Workflow;

/**
 * A hand-written workflow that opts in to be typed as a GenAI {@code invoke_agent} operation through its document
 * metadata.
 */
@ApplicationScoped
public class OptInAgentFlow extends Flow {

    public static final String NAME = "opt-in-agent";
    public static final String AGENT_NAME = "triage";
    public static final String SUMMARY = "Triage an incoming request";

    @Override
    public Workflow descriptor() {
        return workflow(NAME)
                .document(d -> d.summary(SUMMARY)
                        .metadata(m -> m.metadata(WorkflowAgentMetadata.META_AGENT_NAME, AGENT_NAME)))
                .tasks(function("triage", (String input) -> input.toUpperCase(), String.class))
                .build();
    }
}
