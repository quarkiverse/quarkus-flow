package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.internal.WorkflowNameUtils.safeName;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import io.serverlessworkflow.api.types.func.LoopPredicateIndex;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.fluent.func.FuncTaskItemListBuilder;
import io.serverlessworkflow.impl.TaskContext;

public class FlowLoopAgentService<T> extends AbstractFlowAgentService<T, LoopAgentService<T>> implements LoopAgentService<T> {

    private static final String AT = "index";
    private static final String ITEM = "item";
    private static final String EXIT_PROP = "_exit";

    private static final LoopPredicateIndex<AgenticScope, Object> EXIT_COND_END = (scope, item, idx) -> {
        // The temp here improves readability and understanding.
        Boolean exit = scope.readState(EXIT_PROP, false);
        return Boolean.FALSE.equals(exit); // continue while exit == false
    };

    private int maxIterations = Integer.MAX_VALUE;
    private BiPredicate<AgenticScope, Integer> exitCondition = (scope, loopCounter) -> false;
    private boolean checkExitConditionAtLoopEnd = false;

    protected FlowLoopAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowLoopAgentService<UntypedAgent> builder() {
        return new FlowLoopAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowLoopAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowLoopAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    /**
     * Predicate used by whileC(...) when checking at the *start* of the loop.
     * Continue while exitCondition == false.
     */
    protected LoopPredicateIndex<AgenticScope, Object> continuePredicate() {
        return (model, item, idx) -> !exitCondition.test(model, idx);
    }

    @Override
    public LoopAgentService<T> maxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public LoopAgentService<T> exitCondition(Predicate<AgenticScope> exitCondition) {
        this.exitCondition = (scope, loopCounter) -> exitCondition.test(scope);
        return this;
    }

    @Override
    public LoopAgentService<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition) {
        this.exitCondition = exitCondition;
        return this;
    }

    @Override
    public LoopAgentService<T> testExitAtLoopEnd(boolean checkExitConditionAtLoopEnd) {
        this.checkExitConditionAtLoopEnd = checkExitConditionAtLoopEnd;
        return this;
    }

    @Override
    protected Consumer<FuncDoTaskBuilder> doWorkflowTasks(List<AgentExecutor> agentExecutors) {
        return tasks -> tasks.forEach("loop-agent-001",
                does -> does
                        // tasks from
                        .tasks(forDo -> agentExecutors
                                .forEach(agentExecutor -> forDo.function(safeName(agentExecutor.agentInvoker().uniqueName()),
                                        fn -> fn.function(agentExecutor::execute, DefaultAgenticScope.class)
                                                .outputAs((scope, wf, tf) -> agenticScopePassthrough(tf.rawInput())))))
                        // Special case when we check the condition at the end of the loop
                        .tasks(checkExitConditionAtLoopEndFunction())
                        .each(ITEM)
                        .at(AT)
                        .collection(ignored -> IntStream.range(0, maxIterations).boxed().toList())
                        .whileC(checkExitConditionAtLoopEnd ? EXIT_COND_END : continuePredicate()));
    }

    protected Consumer<FuncTaskItemListBuilder> checkExitConditionAtLoopEndFunction() {
        return task -> {
            if (checkExitConditionAtLoopEnd) {
                task.function("check-exit-at-end", fn -> fn.function((scope, wf, tf) -> {
                    // Since it's a do..while, we already passed the first iteration at this stage.
                    final Integer idx = (Integer) ((TaskContext) tf).variables().get(AT) + 1;
                    if (exitCondition.test(scope, idx)) {
                        // writing is expensive (has locks), so we do it once.
                        scope.writeState(EXIT_PROP, true);
                    }
                    return scope;
                }, DefaultAgenticScope.class));
            }
        };
    }
}
