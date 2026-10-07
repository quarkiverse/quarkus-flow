package io.quarkiverse.flow.opentelemetry.it;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.opentelemetry.api.trace.Span;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

/**
 * One task that invokes an agentic system, recording the span that is current inside the task body.
 */
@ApplicationScoped
public class AgenticTracingFlow extends Flow {

    public static final String NAME = "agentic-tracing";
    public static final String TASK_NAME = "runAgents";

    public record Email(String text) {
    }

    /** The span id that was current inside the task body during the last run. */
    public static volatile String lastCurrentSpanId;

    @Inject
    IntakeAgents agents;

    @Override
    public Workflow descriptor() {
        return workflow(NAME)
                .tasks(function(TASK_NAME, this::runAgents, Email.class))
                .build();
    }

    private String runAgents(Email email) {
        lastCurrentSpanId = Span.current().getSpanContext().getSpanId();
        return agents.process(email.text());
    }
}
