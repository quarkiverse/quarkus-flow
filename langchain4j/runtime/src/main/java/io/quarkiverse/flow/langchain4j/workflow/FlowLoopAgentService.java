package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.langchain4j.workflow.FlowAgentServiceUtil.agenticScopePassthrough;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.LoopAgentServiceImpl;
import io.serverlessworkflow.api.types.func.LoopPredicateIndex;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.fluent.func.FuncTaskItemListBuilder;
import io.serverlessworkflow.impl.TaskContext;

public class FlowLoopAgentService<T> extends LoopAgentServiceImpl<T> {

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
    // Sub-agents used to synthesize the workflow
    private final List<AgentExecutor> loopAgents = new ArrayList<>();
    // We need our own copies (base fields are private)
    private int flowMaxIterations = Integer.MAX_VALUE;
    private BiPredicate<AgenticScope, Integer> flowExitCond = (scope, loopCounter) -> false;
    private boolean flowTestExitAtLoopEnd = false;

    protected FlowLoopAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public static FlowLoopAgentService<UntypedAgent> builder() {
        return new FlowLoopAgentService<>(UntypedAgent.class, null);
    }

    public static <T> FlowLoopAgentService<T> builder(Class<T> agentServiceClass) {
        return new FlowLoopAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false, LoopAgent.class));
    }

    /**
     * Predicate used by whileC(...) when checking at the *start* of the loop.
     * Continue while exitCondition == false.
     */
    protected LoopPredicateIndex<AgenticScope, Object> continuePredicate() {
        return (scope, item, idx) -> !flowExitCond.test(scope, idx);
    }

    @Override
    public FlowLoopAgentService<T> maxIterations(int maxIterations) {
        super.maxIterations(maxIterations); // keep upstream behavior consistent
        this.flowMaxIterations = maxIterations;
        return this;
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(String exitConditionDescription,
            BiPredicate<AgenticScope, Integer> exitCondition) {
        this.flowExitCond = exitCondition;
        return super.exitCondition(exitConditionDescription, exitCondition);
    }

    @Override
    public FlowLoopAgentService<T> testExitAtLoopEnd(boolean testExitAtLoopEnd) {
        super.testExitAtLoopEnd(testExitAtLoopEnd);
        this.flowTestExitAtLoopEnd = testExitAtLoopEnd;
        return this;
    }

    // Capture sub-agents so we can synthesize workflow tasks.
    @Override
    public FlowLoopAgentService<T> subAgents(List<AgentExecutor> agentExecutors) {
        super.subAgents(agentExecutors);
        this.loopAgents.addAll(agentExecutors);
        return this;
    }

    @Override
    public T build() {
        final FlowPlanner planner = new FlowPlanner(this.agentServiceClass, this.description, this.tasksDefinition());
        return build(() -> planner);
    }

    protected Consumer<FuncDoTaskBuilder> tasksDefinition() {
        return tasks -> {
            // If we check exit at loop end, reset per workflow invocation (not per iteration!)
            if (flowTestExitAtLoopEnd) {
                tasks.function("loop-reset-exit",
                        fn -> fn.function((DefaultAgenticScope scope) -> {
                            scope.writeState(EXIT_PROP, false);
                            return scope;
                        },
                                DefaultAgenticScope.class).outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
            }

            tasks.forEach("loop",
                    does -> does
                            .tasks(forDo -> FlowAgentServiceUtil.addSequentialAgentTasks(forDo, loopAgents))
                            .tasks(checkExitConditionAtLoopEndFunction())
                            .each(ITEM)
                            .at(AT)
                            .collection(ignored -> IntStream.range(0, flowMaxIterations).boxed().toList())
                            .whileC(flowTestExitAtLoopEnd ? EXIT_COND_END : continuePredicate()));
        };
    }

    protected Consumer<FuncTaskItemListBuilder> checkExitConditionAtLoopEndFunction() {
        return task -> {
            if (flowTestExitAtLoopEnd) {
                task.function("check-exit-at-end", fn -> fn.function((scope, wf, tf) -> {
                    final Integer idx = (Integer) ((TaskContext) tf).variables().get(AT) + 1;
                    if (flowExitCond.test(scope, idx)) {
                        scope.writeState(EXIT_PROP, true);
                    }
                    return scope;
                }, DefaultAgenticScope.class));
            }
        };
    }
}
