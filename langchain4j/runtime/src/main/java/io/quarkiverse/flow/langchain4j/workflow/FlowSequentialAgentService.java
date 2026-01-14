package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.workflow.impl.SequentialAgentServiceImpl;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowSequentialAgentService<T> extends SequentialAgentServiceImpl<T> {

    private final List<AgentExecutor> sequentialAgents = new ArrayList<>();

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
    public FlowSequentialAgentService<T> subAgents(List<AgentExecutor> agentExecutors) {
        super.subAgents(agentExecutors);
        this.sequentialAgents.addAll(agentExecutors);
        return this;
    }

    @Override
    public T build() {
        final FlowPlanner planner = new FlowPlanner(this.agentServiceClass, this.description, this.tasksDefinition());
        return build(() -> planner);
    }

    protected Consumer<FuncDoTaskBuilder> tasksDefinition() {
        return tasks -> FlowAgentServiceUtil.addSequentialAgentTasks(tasks, sequentialAgents);
    }
}
