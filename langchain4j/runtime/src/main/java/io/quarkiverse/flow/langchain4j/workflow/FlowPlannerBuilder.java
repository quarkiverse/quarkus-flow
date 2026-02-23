package io.quarkiverse.flow.langchain4j.workflow;

import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;

final class FlowPlannerBuilder {

    private final FlowAgentService<?> agentService;
    private final FlowAgentWorkflowBuilder workflowBuilder;

    FlowPlannerBuilder(FlowAgentService<?> agentService) {
        this.agentService = agentService;
        this.workflowBuilder = new FlowAgentWorkflowBuilder(agentService.agentServiceClass(),
                agentService.description(),
                agentService.tasksDefinition(),
                agentService.workflowRegistry());
        // Close all the sessions for the given workflow instance id in case of an exception
        ((AbstractServiceBuilder<?, ?>) agentService).errorHandler((ctx -> {
            FlowPlannerSessions.getInstance().close(ctx.agenticScope(), ctx.exception());
            return ErrorRecoveryResult.throwException();
        }));
    }

    /**
     * A new {@link FlowPlanner} reference with cached {@link FlowAgentWorkflowBuilder} instance. At this point, the
     * {@link dev.langchain4j.agentic.planner.AgenticService} must be ready for execution.
     */
    FlowPlanner build() {
        return new FlowPlanner(this.workflowBuilder, this.agentService.topology());
    }

}
