package io.quarkiverse.flow.langchain4j.workflow;

final class FlowPlannerBuilder {

    private final FlowAgentService<?> agentService;
    private final FlowAgentWorkflowBuilder workflowBuilder;

    FlowPlannerBuilder(FlowAgentService<?> agentService) {
        this.agentService = agentService;
        this.workflowBuilder = new FlowAgentWorkflowBuilder(agentService.agentServiceClass(),
                agentService.description(),
                agentService.tasksDefinition(),
                agentService.workflowRegistry());
    }

    /**
     * A new {@link FlowPlanner} reference with cached {@link FlowAgentWorkflowBuilder} instance. At this point, the
     * {@link dev.langchain4j.agentic.planner.AgenticService} must be ready for execution.
     */
    FlowPlanner build() {
        return new FlowPlanner(this.workflowBuilder, this.agentService.topology());
    }

}
