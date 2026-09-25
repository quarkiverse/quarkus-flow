package io.quarkiverse.flow.langchain4j.workflow.flow;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.dsl.FuncTaskItemListBuilder;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndex;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContextData;

public abstract class LoopAgenticFlow extends AgenticFlow {

    private final String AT = "index";
    private final String ITEM = "item";
    private final String loopId = UUID.randomUUID().toString();

    private String exitProperty() {
        // Runtime loops can share an agent interface and the same AgenticScope.
        return agentClassName() + "_" + loopId + "_exit";
    }

    /**
     * Continue until a task evaluates the user's exit predicate to true.
     */
    private final LoopPredicateIndex<AgenticScope, Object> CONTINUE_LOOP = (scope, item, idx) -> {
        Boolean exit = scope.readState(exitProperty(), false);
        return Boolean.FALSE.equals(exit);
    };

    /**
     * Called by the generated {@link io.quarkiverse.flow.Flow} implementing this interface in runtime to build the required
     * exit predicate.
     */
    public BiPredicate<AgenticScope, Integer> buildLoopExitPredicate(Class<?> agentClass, String methodName,
            List<String> paramTypeNames) {
        try {
            Method method = AgenticFlow.findMethodBySignature(agentClass, methodName, paramTypeNames);
            List<AgentArgument> agentArguments = AgentUtil.argumentsFromMethod(method);

            return (agenticScope, loopCounter) -> {
                try {
                    Object[] args = AgentUtil.agentInvocationArguments(
                            agenticScope,
                            agentArguments,
                            Map.of(AgentUtil.AGENTIC_SCOPE_ARG_NAME, agenticScope,
                                    AgentUtil.LOOP_COUNTER_ARG_NAME, loopCounter))
                            .positionalArgs();
                    return (boolean) method.invoke(null, args);
                } catch (Exception e) {
                    throw new RuntimeException("Error invoking exit predicate", e);
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to build exit predicate: " + methodName, e);
        }
    }

    /**
     * Returns the maximum number of iterations for loop workflows.
     * <p>
     * This method is meant to be overridden by generated {@link io.quarkiverse.flow.Flow} classes
     * implementing this interface. The generated implementation returns the value from the
     * {@link dev.langchain4j.agentic.declarative.LoopAgent} annotation's {@code maxIterations} attribute.
     * <p>
     * Default implementation returns 0 (no limit).
     *
     * @return maximum number of iterations, or 0 for unlimited
     */
    public int maxIterations() {
        return 0;
    }

    /**
     * Returns the exit condition predicate for loop workflows.
     * <p>
     * This method is meant to be overridden by generated {@link io.quarkiverse.flow.Flow} classes
     * implementing this interface. The generated implementation calls {@link #buildLoopExitPredicate(Class, String, List)}
     * for the agent's exit condition method and returns the resulting predicate.
     * <p>
     * The predicate receives the current {@link AgenticScope} and loop iteration counter (0-based).
     * Default implementation returns null (no exit condition).
     *
     * @return exit condition predicate, or null if no exit condition
     */
    public BiPredicate<AgenticScope, Integer> exitCondition() {
        return null;
    }

    /**
     * Returns whether the exit condition should be tested at the end of each loop iteration (do-while semantics).
     * <p>
     * This method is meant to be overridden by generated {@link io.quarkiverse.flow.Flow} classes
     * implementing this interface. The generated implementation returns the value from the
     * {@link dev.langchain4j.agentic.declarative.LoopAgent} annotation's {@code testExitAtLoopEnd} attribute.
     * <p>
     * If {@code true}, the exit condition is checked after each iteration (do-while).
     * If {@code false}, the exit condition is checked after each subagent.
     * The first subagent always runs before the first check.
     * Default implementation returns false (while semantics).
     *
     * @return true for do-while semantics, false for while semantics
     */
    public boolean testExitAtLoopEnd() {
        return false;
    }

    // TODO: add as a "description" in the task metadata field once SDK supports it.
    // See: https://github.com/open-workflow-specification/sdk-java/issues/1689
    public String exitConditionDescription() {
        return null;
    }

    protected Consumer<FuncTaskItemListBuilder> checkExitAtEnd(boolean testExitAtLoopEnd,
            BiPredicate<AgenticScope, Integer> exitPredicate) {
        return task -> {
            if (testExitAtLoopEnd) {
                task.function("check-exit-at-end", fn -> fn.function((scope, wf, tf) -> {
                    Integer idx = (Integer) ((TaskContext) tf).variables().get("index") + 1;
                    if (exitPredicate.test(scope, idx)) {
                        scope.writeState(exitProperty(), true);
                    }
                    return scope;
                }, DefaultAgenticScope.class));
            }
        };
    }

    @Override
    public Workflow descriptor() {
        final int max = maxIterations();
        final BiPredicate<AgenticScope, Integer> exit = exitCondition();
        final boolean testAtEnd = testExitAtLoopEnd();

        BiPredicate<AgenticScope, Integer> exitPredicate = exit != null ? exit
                : (scope, idx) -> false;

        return FlowWorkflowBuilder.workflow()
                .document(buildDocument())
                .input(inputSchema())
                .tasks(tasks -> {
                    tasks.function("loop-reset-exit",
                            fn -> fn.function((DefaultAgenticScope scope) -> {
                                scope.writeState(exitProperty(), false);
                                return scope;
                            }, DefaultAgenticScope.class)
                                    .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));

                    tasks.forEach("loop", does -> does
                            .tasks(forDo -> {
                                List<String> taskNames = subAgentTaskNames();
                                for (int i = 0; i < taskNames.size(); i++) {
                                    final int index = i; // capture for lambda
                                    String taskName = taskNames.get(i) + "-" + i;
                                    forDo.function(
                                            taskName,
                                            fn -> {
                                                fn.function(
                                                        (DefaultAgenticScope scope, WorkflowContextData ctx) -> executeAgent(
                                                                ctx.instanceData().id(), scope,
                                                                index),
                                                        DefaultAgenticScope.class)
                                                        .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput()));
                                                if (!testAtEnd && index > 0) {
                                                    fn.when(
                                                            scope -> !Boolean.TRUE
                                                                    .equals(scope.readState(exitProperty(), false)),
                                                            DefaultAgenticScope.class);
                                                }
                                            });
                                    // Cache the result so skipped agents never re-evaluate a user predicate.
                                    if (!testAtEnd) {
                                        forDo.function(
                                                "check-exit-" + index,
                                                fn -> {
                                                    fn.function((scope, wf, tf) -> {
                                                        Integer cycleIndex = (Integer) ((TaskContext) tf).variables()
                                                                .get(AT);
                                                        if (exitPredicate.test(scope, cycleIndex)) {
                                                            scope.writeState(exitProperty(), true);
                                                        }
                                                        return scope;
                                                    }, DefaultAgenticScope.class)
                                                            .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput()));
                                                    fn.when(
                                                            scope -> !Boolean.TRUE
                                                                    .equals(scope.readState(exitProperty(), false)),
                                                            DefaultAgenticScope.class);
                                                });
                                    }
                                }
                            })
                            .tasks(checkExitAtEnd(testAtEnd, exitPredicate))
                            .each(ITEM)
                            .at(AT)
                            .collection(ignored -> IntStream.range(0, max).boxed().toList())
                            .whileC(CONTINUE_LOOP));
                })
                .build();
    }
}
