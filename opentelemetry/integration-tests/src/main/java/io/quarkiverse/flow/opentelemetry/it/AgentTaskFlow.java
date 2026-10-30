package io.quarkiverse.flow.opentelemetry.it;

import static io.quarkiverse.flow.dsl.FlowDSL.agent;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

/**
 * A plain workflow with one Java DSL {@code agent(..)} task, typed as a GenAI {@code invoke_agent} operation, and one
 * plain function task.
 */
@ApplicationScoped
public class AgentTaskFlow extends Flow {

    public static final String NAME = "agent-task";
    public static final String AGENT_TASK_NAME = "triageAgent";
    public static final String PLAIN_TASK_NAME = "plainTask";

    @Override
    public Workflow descriptor() {
        return workflow(NAME)
                .tasks(agent(AGENT_TASK_NAME, (String memoryId, String input) -> input.toUpperCase(), String.class),
                        function(PLAIN_TASK_NAME, (String input) -> input.toLowerCase(), String.class))
                .build();
    }
}
