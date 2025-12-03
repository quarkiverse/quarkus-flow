package io.quarkiverse.flow.langchain4j.workflow;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.agentic.workflow.WorkflowAgentsBuilder;

public class FlowAgentsBuilder implements WorkflowAgentsBuilder {

    @Override
    public SequentialAgentService<UntypedAgent> sequenceBuilder() {
        return FlowSequentialAgentService.builder();
    }

    @Override
    public <T> SequentialAgentService<T> sequenceBuilder(Class<T> agentServiceClass) {
        return FlowSequentialAgentService.builder(agentServiceClass);
    }

    @Override
    public ParallelAgentService<UntypedAgent> parallelBuilder() {
        throw new UnsupportedOperationException("parallelBuilder is not yet implemented");
    }

    @Override
    public <T> ParallelAgentService<T> parallelBuilder(Class<T> agentServiceClass) {
        throw new UnsupportedOperationException("parallelBuilder is not yet implemented");
    }

    @Override
    public LoopAgentService<UntypedAgent> loopBuilder() {
        throw new UnsupportedOperationException("loopBuilder is not yet implemented");
    }

    @Override
    public <T> LoopAgentService<T> loopBuilder(Class<T> agentServiceClass) {
        throw new UnsupportedOperationException("loopBuilder is not yet implemented");
    }

    @Override
    public ConditionalAgentService<UntypedAgent> conditionalBuilder() {
        throw new UnsupportedOperationException("conditionalBuilder is not yet implemented");
    }

    @Override
    public <T> ConditionalAgentService<T> conditionalBuilder(Class<T> agentServiceClass) {
        throw new UnsupportedOperationException("conditionalBuilder is not yet implemented");
    }
}
