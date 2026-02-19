package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.withInstanceId;

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
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.ParallelAgentServiceImpl;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowParallelAgentService<T> extends ParallelAgentServiceImpl<T> implements FlowAgentService {

    private static final Logger LOG = LoggerFactory.getLogger(FlowParallelAgentService.class);

    private final WorkflowRegistry workflowRegistry;

    protected FlowParallelAgentService(Class<T> agentServiceClass, Method agenticMethod, WorkflowRegistry workflowRegistry) {
        super(agentServiceClass, agenticMethod);
        this.workflowRegistry = workflowRegistry;
    }

    public static FlowParallelAgentService<UntypedAgent> builder(WorkflowRegistry workflowRegistry) {
        return new FlowParallelAgentService<>(UntypedAgent.class, null, workflowRegistry);
    }

    public static <T> FlowParallelAgentService<T> builder(Class<T> agentServiceClass, WorkflowRegistry workflowRegistry) {
        return new FlowParallelAgentService<>(agentServiceClass,
                validateAgentClass(agentServiceClass, false, ParallelAgent.class), workflowRegistry);
    }

    @Override
    public FlowParallelAgentService<T> executor(Executor executor) {
        throw new UnsupportedOperationException(
                "Changing the default WorkflowApplication executor is not supported at this time.");
    }

    @Override
    public T build() {
        final FlowAgentServiceWorkflowBuilder workflowBuilder = new FlowAgentServiceWorkflowBuilder(this.agentServiceClass,
                this.description, this.tasksDefinition(), workflowRegistry);
        return build(() -> new FlowPlanner(workflowBuilder, AgenticSystemTopology.PARALLEL));
    }

    @Override
    public Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return ((agents) -> tasks -> tasks.fork("parallel",
                fork -> {
                    int step = 0;
                    for (AgentInstance agent : agents) {
                        final String branchName = safeName(agent.agentId() + "-" + (step++));
                        fork.branches(withInstanceId(branchName, (String instanceId, DefaultAgenticScope scope) -> {
                            CompletableFuture<Void> nextActionFuture = FlowPlannerSessions.getInstance().get(instanceId)
                                    .executeAgent(agent);
                            LOG.debug("Parallel execution of agent {} in branch {} started", agent.agentId(),
                                    branchName);
                            nextActionFuture.join();
                            LOG.debug("Parallel execution of agent {} in branch {} terminated", agent.agentId(),
                                    branchName);
                            return null;
                        },
                                DefaultAgenticScope.class));

                    }
                }));
    }
}
