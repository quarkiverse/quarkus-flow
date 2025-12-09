package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowConditionalAgentService<T> extends AbstractFlowAgentService<T, ConditionalAgentService<T>>
        implements ConditionalAgentService<T> {

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
    public ConditionalAgentService<T> subAgents(Object... agents) {
        return subAgents(agenticScope -> true, Arrays.asList(agents));
    }

    @Override
    public ConditionalAgentService<T> subAgents(Predicate<AgenticScope> condition, Object... agents) {
        return subAgents(condition, agentsToExecutors(agents));
    }

    @Override
    public ConditionalAgentService<T> subAgents(Predicate<AgenticScope> condition, List<AgentExecutor> agentExecutors) {
        this.conditionalAgents.add(new ConditionalAgent(condition, agentExecutors));
        return super.subAgents(agentExecutors);
    }

    @Override
    public ConditionalAgentService<T> subAgent(Predicate<AgenticScope> condition, AgentExecutor agentExecutor) {
        this.conditionalAgents.add(new ConditionalAgent(condition, List.of(agentExecutor)));
        return super.subAgents(List.of(agentExecutor));
    }

    @Override
    protected Consumer<FuncDoTaskBuilder> doWorkflowTasks(List<AgentExecutor> agentExecutors) {
        return tasks -> conditionalAgents.forEach(
                c -> c.agentExecutors
                        .forEach(agentExecutor -> tasks.function(safeName(agentExecutor.agentInvoker().uniqueName()),
                                fn -> fn.function(agentExecutor::execute, DefaultAgenticScope.class)
                                        // Condition
                                        .when(c.condition, AgenticScope.class)
                                        // Add the scope back to the input
                                        .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())))));
    }

    private record ConditionalAgent(Predicate<AgenticScope> condition, List<AgentExecutor> agentExecutors) {
    }
}
