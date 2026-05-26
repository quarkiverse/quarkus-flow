package io.quarkiverse.flow.langchain4j.workflow.runtime;

import java.lang.reflect.Method;
import java.util.Collection;

import dev.langchain4j.agentic.workflow.ParallelAgentService;
import io.quarkiverse.flow.langchain4j.workflow.service.*;

public final class RuntimeFlowParallelAgentService<T> extends FlowParallelAgentService<T> {

    public RuntimeFlowParallelAgentService(Class<T> agentServiceClass, Method agenticMethod, RuntimeParallelAgenticFlow flow) {
        super(agentServiceClass, agenticMethod, flow);
    }

    @Override
    public ParallelAgentService<T> subAgents(Object... agents) {
        ((RuntimeParallelAgenticFlow) flow).addSubAgentTaskName(agents);
        return super.subAgents(agents);
    }

    @Override
    public ParallelAgentService<T> subAgents(Collection<?> agents) {
        // NOTE: Don't add task names here! This method is called by the parent LC4j class
        // as a callback from the varargs overload. Adding task names here would duplicate them.
        return super.subAgents(agents);
    }

    @Override
    public T build() {
        ((RuntimeParallelAgenticFlow) flow).init();
        return super.build();
    }
}
