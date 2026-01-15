package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;
import static io.quarkiverse.flow.langchain4j.workflow.FlowAgentServiceUtil.agenticScopePassthrough;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.ConditionalAgentServiceImpl;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowConditionalAgentService<T> extends ConditionalAgentServiceImpl<T> {

    private final List<ConditionalAgent> conditionalAgents = new ArrayList<>();

    protected FlowConditionalAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowConditionalAgentService<UntypedAgent> builder() {
        return new FlowConditionalAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowConditionalAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowConditionalAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public FlowConditionalAgentService<T> subAgents(Predicate<AgenticScope> condition, List<AgentExecutor> agentExecutors) {
        super.subAgents(condition, agentExecutors);
        this.conditionalAgents.add(new ConditionalAgent(condition, agentExecutors));
        return this;
    }

    @Override
    public FlowConditionalAgentService<T> subAgent(Predicate<AgenticScope> condition, AgentExecutor agentExecutor) {
        super.subAgent(condition, agentExecutor);
        this.conditionalAgents.add(new ConditionalAgent(condition, List.of(agentExecutor)));
        return this;
    }

    @Override
    public T build() {
        final FlowPlanner planner = new FlowPlanner(this.agentServiceClass, this.description, this.tasksDefinition());
        return build(() -> planner);
    }

    protected Consumer<FuncDoTaskBuilder> tasksDefinition() {
        return tasks -> {
            int step = 0;
            for (ConditionalAgent c : conditionalAgents) {
                for (AgentExecutor agentExecutor : c.agentExecutors) {
                    final String stepName = safeName(agentExecutor.agentInvoker().agentId() + "-" + (step++));
                    tasks.function(stepName,
                            fn -> fn.function(
                                    (DefaultAgenticScope scope) -> agentExecutor.syncExecute(scope, null),
                                    DefaultAgenticScope.class)
                                    .when(c.condition, AgenticScope.class)
                                    .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
                }
            }
        };
    }

    private record ConditionalAgent(Predicate<AgenticScope> condition, List<AgentExecutor> agentExecutors) {
    }
}
