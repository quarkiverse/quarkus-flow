package io.quarkiverse.flow.langchain4j.workflow;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.agentic.workflow.WorkflowAgentsBuilder;
import io.quarkus.arc.Arc;

public class FlowAgentsBuilder implements WorkflowAgentsBuilder {

    private FlowAgentsBuilderService services() {
        return Arc.container().instance(FlowAgentsBuilderService.class).get();
    }

    @Override
    public SequentialAgentService<UntypedAgent> sequenceBuilder() {
        return services().newSequential();
    }

    @Override
    public <T> SequentialAgentService<T> sequenceBuilder(Class<T> agentServiceClass) {
        return services().newSequential(agentServiceClass);
    }

    @Override
    public ParallelAgentService<UntypedAgent> parallelBuilder() {
        return services().newParallel();
    }

    @Override
    public <T> ParallelAgentService<T> parallelBuilder(Class<T> agentServiceClass) {
        return services().newParallel(agentServiceClass);
    }

    @Override
    public LoopAgentService<UntypedAgent> loopBuilder() {
        return services().newLoop();
    }

    @Override
    public <T> LoopAgentService<T> loopBuilder(Class<T> agentServiceClass) {
        return services().newLoop(agentServiceClass);
    }

    @Override
    public ConditionalAgentService<UntypedAgent> conditionalBuilder() {
        return services().newConditional();
    }

    @Override
    public <T> ConditionalAgentService<T> conditionalBuilder(Class<T> agentServiceClass) {
        return services().newConditional(agentServiceClass);
    }
}
