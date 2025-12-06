package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowParallelAgentService<T> extends AbstractFlowAgentService<T, ParallelAgentService<T>>
        implements ParallelAgentService<T> {

    protected FlowParallelAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowParallelAgentService<UntypedAgent> builder() {
        return new FlowParallelAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowParallelAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowParallelAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public ParallelAgentService<T> executor(Executor executor) {
        // TODO: if we change the executor here, it will propagate to all workflows within the same application; WorkflowApplication is a singleton, managed bean on Quarkus Flow Runtime.
        throw new UnsupportedOperationException(
                "Changing the default WorkflowApplication executor is not supported at this time.");
    }

    @Override
    protected Consumer<FuncDoTaskBuilder> doWorkflowTasks(List<AgentExecutor> agentExecutors) {
        return tasks -> tasks.fork("parallel-agents-001",
                fork -> agentExecutors.forEach(
                        agentExecutor -> fork.branch(safeName(agentExecutor.agentInvoker().uniqueName()),
                                agentExecutor::execute,
                                DefaultAgenticScope.class)));
    }
}
