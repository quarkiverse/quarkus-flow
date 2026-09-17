package io.quarkiverse.flow.langchain4j.workflow.flow;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgent;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowContextData;

public abstract class ConditionalAgenticFlow extends AgenticFlow {

    private List<ConditionalAgent> conditionalAgentsList = Collections.emptyList();

    /**
     * Build activation predicate using LangChain4j's DeclarativeUtil API. Called by the generated
     * {@link AgenticFlow} extending this class.
     */
    public Predicate<AgenticScope> buildActivationPredicate(Class<?> agentClass, String methodName,
            List<String> paramTypeNames) {
        try {
            return DeclarativeUtil
                    .agenticScopePredicate(AgenticFlow.findMethodBySignature(agentClass, methodName, paramTypeNames));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build activation predicate: " + methodName, e);
        }
    }

    /**
     * Returns activation predicates for conditional workflows, indexed by subagent position.
     * <p>
     * This method is meant to be overridden by generated {@link AgenticFlow} classes
     * extending this class. The generated implementation calls {@link #buildActivationPredicate(Class, String, List)}
     * for each agent's activation condition method and returns a map of subagent indices to their predicates.
     * <p>
     * Default implementation returns an empty map (no conditional routing).
     *
     * @return map of subagent indices to activation predicates
     */
    protected Map<Integer, Predicate<AgenticScope>> activationPredicates() {
        return Map.of();
    }

    /**
     * Sets the list of conditional agents created during builder API usage.
     * This is called by {@link io.quarkiverse.flow.langchain4j.workflow.service.FlowConditionalAgentService}
     * to populate the list from the parent ConditionalAgentServiceImpl.
     *
     * @param agents list of conditional agents to store
     */
    public void setConditionalAgents(List<ConditionalAgent> agents) {
        this.conditionalAgentsList = Collections.unmodifiableList(agents);
    }

    /**
     * Holds the list analog to {@link #activationPredicates()} map. The map is required to guarantee the agent execution order.
     * This list holds the same reference with additional information for user interfaces.
     *
     * @return list of {@link ConditionalAgent}s created during builder API usage or annotation declaration.
     */
    public List<ConditionalAgent> conditionalAgents() {
        return conditionalAgentsList;
    }

    @Override
    public Workflow descriptor() {
        Map<Integer, Predicate<AgenticScope>> predicates = activationPredicates();

        return FlowWorkflowBuilder.workflow()
                .document(buildDocument())
                .input(inputSchema())
                .tasks(tasks -> {
                    List<String> taskNames = subAgentTaskNames();
                    for (int i = 0; i < taskNames.size(); i++) {
                        final int index = i; // capture for lambda
                        String taskName = taskNames.get(i) + "-" + i;
                        Predicate<AgenticScope> predicate = predicates.get(i);
                        tasks.function(
                                taskName,
                                fn -> fn.function(
                                        (DefaultAgenticScope scope, WorkflowContextData ctx) -> executeAgent(
                                                ctx.instanceData().id(), scope,
                                                index),
                                        DefaultAgenticScope.class)
                                        .when(predicate == null ? scope -> true : predicate, AgenticScope.class)
                                        .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
                    }
                })
                .build();
    }
}
