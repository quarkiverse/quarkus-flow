package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.workflow.impl.ConditionalAgentServiceImpl;

public class FlowConditionalAgentService<T> extends ConditionalAgentServiceImpl<T> {

    private final ConditionalAgenticFlow flow;

    protected FlowConditionalAgentService(Class<T> agentServiceClass, Method agenticMethod, ConditionalAgenticFlow flow) {
        super(agentServiceClass, agenticMethod);
        this.flow = flow;
    }

    public static FlowConditionalAgentService<UntypedAgent> builder() {
        return new FlowConditionalAgentService<>(UntypedAgent.class, null, null);
    }

    public static <T> FlowConditionalAgentService<T> builder(Class<T> agentServiceClass, ConditionalAgenticFlow flow) {
        return new FlowConditionalAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, ConditionalAgent.class), flow);
    }

    @Override
    public T build() {
        return build(() -> new FlowPlanner(AgenticSystemTopology.ROUTER, flow));
    }
}
