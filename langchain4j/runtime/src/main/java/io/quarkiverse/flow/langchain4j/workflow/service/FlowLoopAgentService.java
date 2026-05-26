package io.quarkiverse.flow.langchain4j.workflow.service;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.workflow.impl.LoopAgentServiceImpl;
import io.quarkiverse.flow.langchain4j.workflow.flow.LoopAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeFlowLoopAgentService;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeLoopAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeWorkflowApplicationProvider;

public class FlowLoopAgentService<T> extends LoopAgentServiceImpl<T> {

    protected final LoopAgenticFlow flow;

    protected FlowLoopAgentService(Class<T> agentServiceClass, Method agenticMethod, LoopAgenticFlow flow) {
        super(agentServiceClass, agenticMethod);
        this.flow = flow;
    }

    public static RuntimeFlowLoopAgentService<UntypedAgent> builder(RuntimeWorkflowApplicationProvider runtimeAppProvider) {
        return new RuntimeFlowLoopAgentService<>(UntypedAgent.class, null,
                new RuntimeLoopAgenticFlow(UntypedAgent.class.getName(), runtimeAppProvider));
    }

    public static <T> FlowLoopAgentService<T> builder(Class<T> agentServiceClass, LoopAgenticFlow flow) {
        return new FlowLoopAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false, LoopAgent.class),
                flow);
    }

    public static <T> RuntimeFlowLoopAgentService<T> builder(Class<T> agentServiceClass,
            RuntimeWorkflowApplicationProvider runtimeAppProvider) {
        return new RuntimeFlowLoopAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, LoopAgent.class),
                new RuntimeLoopAgenticFlow(agentServiceClass.getName(), runtimeAppProvider));
    }

    @Override
    public T build() {
        return build(() -> new FlowPlanner(AgenticSystemTopology.LOOP, flow));
    }
}
