package io.quarkiverse.flow.langchain4j.workflow;

import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import io.quarkus.arc.Unremovable;

/**
 * Bridge between LangChain4j {@link dev.langchain4j.agentic.workflow.WorkflowAgentsBuilder} (loaded as ServiceLoader) and
 * CDI.
 * <p>
 * This service provides access to build-time generated {@link AgenticFlow} implementations
 * and creates the appropriate {@link dev.langchain4j.agentic.AgenticServices} wrappers.
 */
@ApplicationScoped
@Unremovable
public class FlowAgentsBuilderService {

    @Inject
    @Any
    Instance<SequentialAgenticFlow> sequentialFlows;

    @Inject
    @Any
    Instance<ParallelAgenticFlow> parallelFlows;

    @Inject
    @Any
    Instance<LoopAgenticFlow> loopFlows;

    @Inject
    @Any
    Instance<ConditionalAgenticFlow> conditionalFlows;

    public SequentialAgentService<UntypedAgent> newSequential() {
        throw new UnsupportedOperationException("Untyped agents are not supported yet");
    }

    public <T> SequentialAgentService<T> newSequential(Class<T> agentServiceClass) {
        return FlowSequentialAgentService.builder(agentServiceClass,
                sequentialFlows.stream()
                        .filter(flow -> flow.agentClassName().equals(agentServiceClass.getName()))
                        .findFirst()
                        .orElseThrow(() -> flowNotFoundError("sequential", agentServiceClass,
                                sequentialFlows)));
    }

    public ParallelAgentService<UntypedAgent> newParallel() {
        throw new UnsupportedOperationException("Untyped agents are not supported yet");
    }

    public <T> ParallelAgentService<T> newParallel(Class<T> agentServiceClass) {
        return FlowParallelAgentService.builder(agentServiceClass,
                parallelFlows.stream()
                        .filter(flow -> flow.agentClassName().equals(agentServiceClass.getName()))
                        .findFirst()
                        .orElseThrow(() -> flowNotFoundError("parallel", agentServiceClass,
                                parallelFlows)));
    }

    public LoopAgentService<UntypedAgent> newLoop() {
        throw new UnsupportedOperationException("Untyped agents are not supported yet");
    }

    public <T> LoopAgentService<T> newLoop(Class<T> agentServiceClass) {
        return FlowLoopAgentService.builder(agentServiceClass,
                loopFlows.stream()
                        .filter(flow -> flow.agentClassName().equals(agentServiceClass.getName()))
                        .findFirst()
                        .orElseThrow(() -> flowNotFoundError("loop", agentServiceClass,
                                loopFlows)));
    }

    public ConditionalAgentService<UntypedAgent> newConditional() {
        throw new UnsupportedOperationException("Untyped agents are not supported yet");
    }

    public <T> ConditionalAgentService<T> newConditional(Class<T> agentServiceClass) {
        return FlowConditionalAgentService.builder(agentServiceClass,
                conditionalFlows.stream()
                        .filter(flow -> flow.agentClassName().equals(agentServiceClass.getName()))
                        .findFirst()
                        .orElseThrow(() -> flowNotFoundError("conditional", agentServiceClass,
                                conditionalFlows)));
    }

    private static <T extends AgenticFlow> IllegalStateException flowNotFoundError(
            String topology,
            Class<?> agentServiceClass,
            Instance<T> availableFlows) {
        String available = availableFlows.stream()
                .map(AgenticFlow::agentClassName)
                .collect(Collectors.joining(", "));

        return new IllegalStateException(
                "No " + topology + " flow found for agent: " + agentServiceClass.getName() +
                        ". Available " + topology + " flows: [" +
                        (available.isEmpty() ? "none" : available) + "]. " +
                        "Verify the agent interface has the correct @" + capitalize(topology) + "Agent annotation.");
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
