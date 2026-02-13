package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.workflow.impl.SequentialAgentServiceImpl;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowSequentialAgentService<T> extends SequentialAgentServiceImpl<T> implements FlowAgentService {

    protected FlowSequentialAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowSequentialAgentService<UntypedAgent> builder() {
        return new FlowSequentialAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowSequentialAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowSequentialAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, SequenceAgent.class));
    }

    @Override
    public T build() {
return build(() -> new FlowPlanner(this.agentServiceClass, this.description, this.tasksDefinition()));
    }

    public BiFunction<FlowPlanner, InitPlanningContext, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return (flowPlanner, initPlanningContext) -> tasks -> FlowAgentServiceUtil.addAgentTasks(tasks, flowPlanner,
                initPlanningContext.subagents());
    }
}
