package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.ParallelAgentServiceImpl;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowParallelAgentService<T> extends ParallelAgentServiceImpl<T> implements FlowAgentService {

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
    public FlowParallelAgentService<T> executor(Executor executor) {
        throw new UnsupportedOperationException(
                "Changing the default WorkflowApplication executor is not supported at this time.");
    }

    @Override
    public T build() {
        final FlowPlanner planner = new FlowPlanner(this.agentServiceClass, this.description, this.tasksDefinition());
        return build(() -> planner);
    }

    @Override
    public BiFunction<FlowPlanner, InitPlanningContext, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return ((planner, initPlanningContext) -> tasks -> tasks.fork("parallel",
                fork -> {
                    int step = 0;
                    for (AgentInstance agent : initPlanningContext.subagents()) {
                        final String branchName = safeName(agent.agentId() + "-" + (step++));
                        fork.branch(branchName,
                                (DefaultAgenticScope scope) -> {
                                    CompletableFuture<Void> nextActionFuture = planner.executeAgents(List.of(agent));
                                    return nextActionFuture.join();
                                },
                                DefaultAgenticScope.class);
                    }
                }));
    }
}
