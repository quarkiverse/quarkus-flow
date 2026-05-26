package io.quarkiverse.flow.langchain4j.workflow.runtime;

import java.lang.reflect.Method;
import java.util.Collection;

import dev.langchain4j.agentic.workflow.SequentialAgentService;
import io.quarkiverse.flow.langchain4j.workflow.service.*;

public final class RuntimeFlowSequentialAgentService<T> extends FlowSequentialAgentService<T> {

    public RuntimeFlowSequentialAgentService(Class<T> agentServiceClass, Method agenticMethod,
            RuntimeSequentialAgenticFlow flow) {
        super(agentServiceClass, agenticMethod, flow);
    }

    @Override
    public SequentialAgentService<T> subAgents(Object... agents) {
        ((RuntimeSequentialAgenticFlow) flow).addSubAgentTaskName(agents);
        return super.subAgents(agents);
    }

    @Override
    public SequentialAgentService<T> subAgents(Collection<?> agents) {
        // NOTE: Don't add task names here! This method is called by the parent LC4j class
        // as a callback from the varargs overload. Adding task names here would duplicate them.
        return super.subAgents(agents);
    }

    @Override
    public T build() {
        ((RuntimeSequentialAgenticFlow) flow).init();
        return super.build();
    }
}
