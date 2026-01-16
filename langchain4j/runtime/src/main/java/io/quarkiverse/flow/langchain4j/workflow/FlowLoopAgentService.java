package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.langchain4j.workflow.FlowAgentServiceUtil.agenticScopePassthrough;

import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.LoopAgentServiceImpl;
import io.serverlessworkflow.api.types.func.LoopPredicateIndex;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.fluent.func.FuncTaskItemListBuilder;
import io.serverlessworkflow.impl.TaskContext;

public class FlowLoopAgentService<T> extends LoopAgentServiceImpl<T> implements FlowAgentService {

    private static final String AT = "index";
    private static final String ITEM = "item";
    private static final String EXIT_PROP = "_exit";

    /**
     * For do..while semantics (check at end): continue while exit == false.
     */
    private static final LoopPredicateIndex<AgenticScope, Object> EXIT_COND_END = (scope, item, idx) -> {
        Boolean exit = scope.readState(EXIT_PROP, false);
        return Boolean.FALSE.equals(exit);
    };
    // We need our own copies (base fields are private)
    private int maxIterations = Integer.MAX_VALUE;
    private BiPredicate<AgenticScope, Integer> exitCondition = (scope, loopCounter) -> false;
    private boolean testExitAtLoopEnd = false;

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
        return (scope, item, idx) -> !exitCondition.test(scope, idx);
    }

    @Override
    public FlowLoopAgentService<T> maxIterations(int maxIterations) {
        super.maxIterations(maxIterations); // keep upstream behavior consistent
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public FlowLoopAgentService<T> exitCondition(Predicate<AgenticScope> exitCondition) {
        super.exitCondition(exitCondition);
        this.exitCondition = (scope, loopCounter) -> exitCondition.test(scope);
        return this;
    }

    @Override
    public FlowLoopAgentService<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition) {
        super.exitCondition(exitCondition);
        this.exitCondition = exitCondition;
        return this;
    }

    @Override
    public FlowLoopAgentService<T> testExitAtLoopEnd(boolean testExitAtLoopEnd) {
        super.testExitAtLoopEnd(testExitAtLoopEnd);
        this.testExitAtLoopEnd = testExitAtLoopEnd;
        return this;
    }

    @Override
    public T build() {
        final FlowPlanner planner = new FlowPlanner(this.agentServiceClass, this.description, this.tasksDefinition());
        return build(() -> planner);
    }

    @Override
    public BiFunction<FlowPlanner, InitPlanningContext, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return (planner, initPlanningContext) -> tasks -> {
            // If we check exit at loop end, reset per workflow invocation (not per iteration!)
            if (testExitAtLoopEnd) {
                tasks.function("loop-reset-exit",
                        fn -> fn.function((DefaultAgenticScope scope) -> {
                            scope.writeState(EXIT_PROP, false);
                            return scope;
                        },
                                DefaultAgenticScope.class).outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
            }

            tasks.forEach("loop",
                    does -> does
                            .tasks(forDo -> FlowAgentServiceUtil.addAgentTasks(forDo, planner, initPlanningContext.subagents()))
                            .tasks(checkExitConditionAtLoopEndFunction())
                            .each(ITEM)
                            .at(AT)
                            .collection(ignored -> IntStream.range(0, maxIterations).boxed().toList())
                            .whileC(testExitAtLoopEnd ? EXIT_COND_END : continuePredicate()));
        };
    }

    protected Consumer<FuncTaskItemListBuilder> checkExitConditionAtLoopEndFunction() {
        return task -> {
            if (testExitAtLoopEnd) {
                task.function("check-exit-at-end", fn -> fn.function((scope, wf, tf) -> {
                    final Integer idx = (Integer) ((TaskContext) tf).variables().get(AT) + 1;
                    if (exitCondition.test(scope, idx)) {
                        scope.writeState(EXIT_PROP, true);
                    }
                    return scope;
                }, DefaultAgenticScope.class));
            }
        };
    }
}
