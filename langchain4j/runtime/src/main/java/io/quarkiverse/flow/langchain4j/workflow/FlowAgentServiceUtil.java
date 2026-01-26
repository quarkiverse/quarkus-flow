package io.quarkiverse.flow.langchain4j.workflow;

import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.util.List;

import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.serverlessworkflow.fluent.func.spi.FuncDoFluent;
import io.serverlessworkflow.impl.WorkflowModel;

public final class FlowAgentServiceUtil {
    private FlowAgentServiceUtil() {
    }

    public static final String INVOKER_KIND_AGENTIC_LC4J = "agentic-lc4j";

    /**
     * Helper for the very common “AgenticScope passthrough” output mapping.
     * Reuse this in all Flow-*AgentService implementations that
     * want to keep AgenticScope as the thread of data between tasks.
     */
    static Object agenticScopePassthrough(WorkflowModel rawInput) {
        Object raw = rawInput.asJavaObject();
        if (raw instanceof AgenticScope scope) {
            return scope;
        }
        throw new IllegalStateException("Expected AgenticScope but got " + raw);
    }

    /**
     * Adds a straight sequence of agent calls as Flow function tasks.
     * Uses syncExecute(scope, null) and keeps AgenticScope as the thread of data.
     */
    static void addSequentialAgentTasks(FuncDoFluent<?> tasks, List<AgentExecutor> executors) {
        for (int i = 0; i < executors.size(); i++) {
            AgentExecutor executor = executors.get(i);
            String stepName = safeName(executor.agentInvoker().agentId() + "-" + i);
            tasks.function(stepName,
                    fn -> fn.function(
                            (DefaultAgenticScope scope) -> executor.syncExecute(scope, null),
                            DefaultAgenticScope.class)
                            .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
        }
    }

}
