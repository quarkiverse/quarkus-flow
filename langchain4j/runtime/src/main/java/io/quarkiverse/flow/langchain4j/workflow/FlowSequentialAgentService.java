package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;

public class FlowSequentialAgentService<T>
        extends AbstractFlowAgentService<T, SequentialAgentService<T>>
        implements SequentialAgentService<T> {

    protected FlowSequentialAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowSequentialAgentService<UntypedAgent> builder() {
        return new FlowSequentialAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowSequentialAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowSequentialAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    protected Consumer<FuncDoTaskBuilder> doWorkflowTasks(List<AgentExecutor> agentExecutors) {
        // Sequential pattern: each agent is a simple “execute with AgenticScope” task.
        return tasks -> agentExecutors
                .forEach(agentExecutor -> tasks.function(safeName(agentExecutor.agentInvoker().uniqueName()),
                        fn -> fn.function(agentExecutor::execute, DefaultAgenticScope.class)
                                .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput()))));
    }
}
