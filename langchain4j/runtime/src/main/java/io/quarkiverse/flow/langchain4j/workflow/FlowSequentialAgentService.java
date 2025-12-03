package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;
import java.util.List;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.workflow.SequentialAgentService;

public class FlowSequentialAgentService<T>
        extends AbstractFlowAgentService<T, SequentialAgentService<T>>
        implements SequentialAgentService<T> {

    protected FlowSequentialAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowSequentialAgentService<UntypedAgent> builder() {
        return new FlowSequentialAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowSequentialAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowSequentialAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    protected void configureWorkflow(List<AgentExecutor> agentExecutors) {
        // Sequential pattern: each agent is a simple “execute with AgenticScope” task.
        agentExecutors.forEach(this::addSimpleAgentTask);
    }
}
