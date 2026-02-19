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

public class FlowSequentialAgentService<T> extends SequentialAgentServiceImpl<T> implements FlowAgentService {

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
        final FlowAgentServiceWorkflowBuilder workflowBuilder = new FlowAgentServiceWorkflowBuilder(this.agentServiceClass,
                this.description, this.tasksDefinition(), workflowRegistry);
        return build(() -> new FlowPlanner(workflowBuilder, AgenticSystemTopology.SEQUENCE));
    }

    public Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return (agents) -> tasks -> FlowAgentServiceUtil.addAgentTasks(tasks, agents);
    }
}
