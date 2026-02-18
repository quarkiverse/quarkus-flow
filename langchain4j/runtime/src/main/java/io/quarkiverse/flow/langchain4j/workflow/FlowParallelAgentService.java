package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.ParallelAgentServiceImpl;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowParallelAgentService<T> extends ParallelAgentServiceImpl<T> implements FlowAgentService {

    private static final Logger LOG = LoggerFactory.getLogger(FlowParallelAgentService.class);

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
        final FlowAgentServiceWorkflowBuilder workflowBuilder = new FlowAgentServiceWorkflowBuilder(this.agentServiceClass,
                this.description, this.tasksDefinition());
        return build(() -> new FlowPlanner(workflowBuilder));
    }

    @Override
    public Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return ((agents) -> tasks -> tasks.fork("parallel",
                fork -> {
                    int step = 0;
                    for (AgentInstance agent : agents) {
                        final String branchName = safeName(agent.agentId() + "-" + (step++));
                        fork.branch(branchName,
                                (DefaultAgenticScope scope) -> {
                                    CompletableFuture<Void> nextActionFuture = FlowPlannerSessions.INSTANCE.execute(scope,
                                            agent);
                                    LOG.debug("Parallel execution of agent {} in branch {} started", agent.agentId(),
                                            branchName);
                                    nextActionFuture.join();
                                    LOG.debug("Parallel execution of agent {} in branch {} terminated", agent.agentId(),
                                            branchName);
                                    return null;
                                },
                                DefaultAgenticScope.class);
                    }
                }));
    }
}
