package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.workflow.impl.LoopAgentServiceImpl;

public class FlowLoopAgentService<T> extends LoopAgentServiceImpl<T> {

    private final LoopAgenticFlow flow;

    protected FlowLoopAgentService(Class<T> agentServiceClass, Method agenticMethod, LoopAgenticFlow flow) {
        super(agentServiceClass, agenticMethod);
        this.flow = flow;
    }

    public static FlowLoopAgentService<UntypedAgent> builder() {
        return new FlowLoopAgentService<>(UntypedAgent.class, null, null);
    }

    public static <T> FlowLoopAgentService<T> builder(Class<T> agentServiceClass, LoopAgenticFlow flow) {
        return new FlowLoopAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false, LoopAgent.class),
                flow);
    }

    @Override
    public T build() {
        return build(() -> new FlowPlanner(AgenticSystemTopology.LOOP, flow));
    }
}
