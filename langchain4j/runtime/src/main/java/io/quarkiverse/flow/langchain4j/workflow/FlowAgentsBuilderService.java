package io.quarkiverse.flow.langchain4j.workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import io.quarkiverse.flow.internal.WorkflowRegistry;

/**
 * Bridge between LC4J {@link dev.langchain4j.agentic.workflow.WorkflowAgentsBuilder} (loaded as ServiceLoader) and CDI
 */
@ApplicationScoped
public class FlowAgentsBuilderService {

    @Inject
    WorkflowRegistry workflowRegistry;

    public SequentialAgentService<UntypedAgent> newSequential() {
        return FlowSequentialAgentService.builder(workflowRegistry);
    }

    public <T> SequentialAgentService<T> newSequential(Class<T> agentServiceClass) {
        return FlowSequentialAgentService.builder(agentServiceClass, workflowRegistry);
    }

    public ParallelAgentService<UntypedAgent> newParallel() {
        return FlowParallelAgentService.builder(workflowRegistry);
    }

    public <T> ParallelAgentService<T> newParallel(Class<T> agentServiceClass) {
        return FlowParallelAgentService.builder(agentServiceClass, workflowRegistry);
    }

    public LoopAgentService<UntypedAgent> newLoop() {
        return FlowLoopAgentService.builder(workflowRegistry);
    }

    public <T> LoopAgentService<T> newLoop(Class<T> agentServiceClass) {
        return FlowLoopAgentService.builder(agentServiceClass, workflowRegistry);
    }

    public ConditionalAgentService<UntypedAgent> newConditional() {
        return FlowConditionalAgentService.builder(workflowRegistry);
    }

    public <T> ConditionalAgentService<T> newConditional(Class<T> agentServiceClass) {
        return FlowConditionalAgentService.builder(agentServiceClass, workflowRegistry);
    }
}
