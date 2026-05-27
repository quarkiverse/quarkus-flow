package io.quarkiverse.flow.langchain4j.workflow.service;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.workflow.impl.ParallelAgentServiceImpl;
import io.quarkiverse.flow.langchain4j.workflow.flow.*;
import io.quarkiverse.flow.langchain4j.workflow.runtime.*;

public class FlowParallelAgentService<T> extends ParallelAgentServiceImpl<T> {

    protected final ParallelAgenticFlow flow;

    protected FlowParallelAgentService(Class<T> agentServiceClass, Method agenticMethod, ParallelAgenticFlow flow) {
        super(agentServiceClass, agenticMethod);
        this.flow = flow;
    }

    public static RuntimeFlowParallelAgentService<UntypedAgent> builder(RuntimeWorkflowApplicationProvider runtimeAppProvider) {
        return new RuntimeFlowParallelAgentService<>(UntypedAgent.class, null,
                new RuntimeParallelAgenticFlow(UntypedAgent.class.getName(), runtimeAppProvider));
    }

    public static <T> FlowParallelAgentService<T> builder(Class<T> agentServiceClass, ParallelAgenticFlow flow) {
        return new FlowParallelAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, ParallelAgent.class), flow);
    }

    public static <T> RuntimeFlowParallelAgentService<T> builder(Class<T> agentServiceClass,
            RuntimeWorkflowApplicationProvider runtimeAppProvider) {
        return new RuntimeFlowParallelAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, ParallelAgent.class),
                new RuntimeParallelAgenticFlow(agentServiceClass.getName(), runtimeAppProvider));
    }

    @Override
    public FlowParallelAgentService<T> executor(Executor executor) {
        throw new UnsupportedOperationException(
                "Changing the default WorkflowApplication executor is not supported at this time.");
    }

    @Override
    public T build() {
        return build(() -> new FlowPlanner(AgenticSystemTopology.PARALLEL, flow));
    }

}
