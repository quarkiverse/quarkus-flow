package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.ParallelAgentServiceImpl;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowParallelAgentService<T> extends ParallelAgentServiceImpl<T> {

    private final List<AgentExecutor> parallelAgents = new ArrayList<>();

    protected FlowParallelAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowParallelAgentService<UntypedAgent> builder() {
        return new FlowParallelAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowParallelAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowParallelAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, ParallelAgent.class));
    }

    @Override
    public FlowParallelAgentService<T> subAgents(List<AgentExecutor> agentExecutors) {
        super.subAgents(agentExecutors);
        this.parallelAgents.addAll(agentExecutors);
        return this;
    }

    @Override
    public FlowParallelAgentService<T> executor(Executor executor) {
        throw new UnsupportedOperationException(
                "Changing the default WorkflowApplication executor is not supported at this time.");
    }

    @Override
    public T build() {
        final FlowPlanner planner = new FlowPlanner(this.agentServiceClass, this.description, this.tasksDefinition());
        return build(() -> planner);
    }

    protected Consumer<FuncDoTaskBuilder> tasksDefinition() {
        return tasks -> tasks.fork("parallel",
                fork -> {
                    int step = 0;
                    for (AgentExecutor agentExecutor : parallelAgents) {
                        final String branchName = safeName(agentExecutor.agentInvoker().agentId() + "-" + (step++));
                        fork.branch(branchName,
                                (DefaultAgenticScope scope) -> agentExecutor.syncExecute(scope, null),
                                DefaultAgenticScope.class);
                    }
                });
    }
}
