package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.workflow.impl.SequentialAgentServiceImpl;

public class FlowSequentialAgentService<T> extends SequentialAgentServiceImpl<T> {

    private final SequentialAgenticFlow flow;

    protected FlowSequentialAgentService(Class<T> agentServiceClass, Method agenticMethod, SequentialAgenticFlow flow) {
        super(agentServiceClass, agenticMethod);
        this.flow = flow;
    }

    public static FlowSequentialAgentService<UntypedAgent> builder() {
        // TODO: build a runtime flow for untyped agents - future PR.
        return new FlowSequentialAgentService<>(UntypedAgent.class, null, null);
    }

    public static <T> FlowSequentialAgentService<T> builder(Class<T> agentServiceClass, SequentialAgenticFlow flow) {
        return new FlowSequentialAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, SequenceAgent.class), flow);
    }

    @Override
    public T build() {
        return build(() -> new FlowPlanner(AgenticSystemTopology.SEQUENCE, this.flow));
    }

}
