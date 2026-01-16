package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;
import static io.quarkiverse.flow.langchain4j.workflow.FlowAgentServiceUtil.agenticScopePassthrough;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.ConditionalAgentServiceImpl;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowConditionalAgentService<T> extends ConditionalAgentServiceImpl<T> implements FlowAgentService {

    private final Map<Integer, Predicate<AgenticScope>> conditions = new HashMap<>();

    protected FlowConditionalAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowConditionalAgentService<UntypedAgent> builder() {
        return new FlowConditionalAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowConditionalAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowConditionalAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, dev.langchain4j.agentic.declarative.ConditionalAgent.class));
    }

    @Override
    public FlowConditionalAgentService<T> subAgents(Predicate<AgenticScope> condition, List<AgentExecutor> agentExecutors) {
        super.subAgents(condition, agentExecutors);
        for (AgentExecutor agentExecutor : agentExecutors) {
            this.conditions.put(agentExecutor.hashCode(), condition);
        }
        return this;
    }

    @Override
    public FlowConditionalAgentService<T> subAgent(Predicate<AgenticScope> condition, AgentExecutor agentExecutor) {
        super.subAgent(condition, agentExecutor);
        this.conditions.put(agentExecutor.hashCode(), condition);
        return this;
    }

    @Override
    public T build() {
        final FlowPlanner planner = new FlowPlanner(this.agentServiceClass, this.description, this.tasksDefinition());
        return build(() -> planner);
    }

    public BiFunction<FlowPlanner, InitPlanningContext, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return (planner, initPlanningContext) -> tasks -> {
            int step = 0;
            for (AgentInstance agent : initPlanningContext.subagents()) {
                final String stepName = safeName(agent.agentId() + "-" + (step++));
                tasks.function(stepName,
                        fn -> fn.function(
                                (DefaultAgenticScope scope) -> {
                                    CompletableFuture<Void> nextActionFuture = planner.executeAgents(List.of(agent));
                                    return nextActionFuture.join();
                                },
                                DefaultAgenticScope.class)
                                .when(this.conditions.get(agent.hashCode()), AgenticScope.class)
                                .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
            }

        };
    }
}
