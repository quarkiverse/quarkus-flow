package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.workflow.impl.SequentialAgentServiceImpl;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowSequentialAgentService<T> extends SequentialAgentServiceImpl<T> implements FlowAgentService<T> {

    private final WorkflowRegistry workflowRegistry;

    protected FlowSequentialAgentService(Class<T> agentServiceClass, Method agenticMethod, WorkflowRegistry workflowRegistry) {
        super(agentServiceClass, agenticMethod);
        this.workflowRegistry = workflowRegistry;
    }

    public static FlowSequentialAgentService<UntypedAgent> builder(WorkflowRegistry workflowRegistry) {
        return new FlowSequentialAgentService<>(UntypedAgent.class, null, workflowRegistry);
    }

    public static <T> FlowSequentialAgentService<T> builder(Class<T> agentServiceClass, WorkflowRegistry workflowRegistry) {
        return new FlowSequentialAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, SequenceAgent.class), workflowRegistry);
    }

    @Override
    public T build() {
        final FlowPlannerBuilder builder = new FlowPlannerBuilder(this);
        return build(builder::build);
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public WorkflowRegistry workflowRegistry() {
        return this.workflowRegistry;
    }

    @Override
    public Class<T> agentServiceClass() {
        return this.agentServiceClass;
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }

    public Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return (agents) -> tasks -> FlowAgentServiceUtil.addAgentTasks(tasks, agents);
    }
}
