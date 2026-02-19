package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;
import static io.quarkiverse.flow.langchain4j.workflow.FlowAgentServiceUtil.agenticScopePassthrough;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.ConditionalAgentServiceImpl;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.impl.WorkflowContextData;

public class FlowConditionalAgentService<T> extends ConditionalAgentServiceImpl<T> implements FlowAgentService {

    private final Map<AgentInstance, Predicate<AgenticScope>> conditions = new IdentityHashMap<>();

    private final WorkflowRegistry workflowRegistry;
    private final FlowPlannerFactory flowPlannerFactory;

    protected FlowConditionalAgentService(Class<T> agentServiceClass, Method agenticMethod, WorkflowRegistry workflowRegistry,
            FlowPlannerFactory flowPlannerFactory) {
        super(agentServiceClass, agenticMethod);
        this.workflowRegistry = workflowRegistry;
        this.flowPlannerFactory = flowPlannerFactory;
    }

    public static FlowConditionalAgentService<UntypedAgent> builder(WorkflowRegistry workflowRegistry,
            FlowPlannerFactory flowPlannerFactory) {
        return new FlowConditionalAgentService<>(UntypedAgent.class, null, workflowRegistry, flowPlannerFactory);
    }

    public static <T> FlowConditionalAgentService<T> builder(Class<T> agentServiceClass, WorkflowRegistry workflowRegistry,
            FlowPlannerFactory flowPlannerFactory) {
        return new FlowConditionalAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, ConditionalAgent.class), workflowRegistry, flowPlannerFactory);
    }

    @Override
    public FlowConditionalAgentService<T> subAgents(String conditionDescription, Predicate<AgenticScope> condition,
            List<AgentExecutor> agentExecutors) {
        super.subAgents(conditionDescription, condition, agentExecutors);
        for (AgentExecutor agentExecutor : agentExecutors) {
            this.conditions.compute(agentExecutor, (k, v) -> (v == null) ? condition : v.or(condition));
        }
        return this;
    }

    @Override
    public FlowConditionalAgentService<T> subAgent(String conditionDescription, Predicate<AgenticScope> condition,
            AgentExecutor agentExecutor) {
        super.subAgent(conditionDescription, condition, agentExecutor);
        this.conditions.compute(agentExecutor, (k, v) -> (v == null) ? condition : v.or(condition));
        return this;
    }

    @Override
    public T build() {
        final FlowAgentServiceWorkflowBuilder workflowBuilder = new FlowAgentServiceWorkflowBuilder(this.agentServiceClass,
                this.description, this.tasksDefinition(), workflowRegistry);
        return build(() -> flowPlannerFactory.newPlanner(workflowBuilder));
    }

    public Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return (agents) -> tasks -> {
            int step = 0;
            for (AgentInstance agent : agents) {
                final String stepName = safeName(agent.agentId() + "-" + (step++));
                tasks.function(stepName,
                        fn -> fn.function(
                                (DefaultAgenticScope scope, WorkflowContextData ctx) -> {
                                    CompletableFuture<Void> nextActionFuture = FlowPlannerSessions.getInstance()
                                            .get(ctx.instanceData().id()).executeAgent(agent);
                                    return nextActionFuture.join();
                                },
                                DefaultAgenticScope.class)
                                .when(this.conditions.get(agent), AgenticScope.class)
                                .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
            }
        };
    }
}
