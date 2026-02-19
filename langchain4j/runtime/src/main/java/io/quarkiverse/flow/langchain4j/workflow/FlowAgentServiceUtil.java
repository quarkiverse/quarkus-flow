package io.quarkiverse.flow.langchain4j.workflow;

import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.serverlessworkflow.fluent.func.spi.FuncDoFluent;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowModel;

public final class FlowAgentServiceUtil {
    public static final String INVOKER_KIND_AGENTIC_LC4J = "agentic-lc4j";

    private FlowAgentServiceUtil() {
    }

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
     */
    static void addAgentTasks(FuncDoFluent<?> tasks, List<AgentInstance> agents) {
        for (int i = 0; i < agents.size(); i++) {
            AgentInstance agent = agents.get(i);
            String stepName = safeName(agent.agentId() + "-" + i);
            tasks.function(stepName,
                    fn -> fn.function(
                            (DefaultAgenticScope scope, WorkflowContextData ctx) -> {
                                CompletableFuture<Void> nextActionFuture = FlowPlannerSessions.getInstance()
                                        .get(ctx.instanceData().id()).executeAgent(agent);
                                return nextActionFuture.join();
                            },
                            DefaultAgenticScope.class)
                            .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
        }
    }
}
