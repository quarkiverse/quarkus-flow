package io.quarkiverse.flow.langchain4j.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static io.quarkiverse.flow.langchain4j.workflow.FlowAgentServiceUtil.agenticScopePassthrough;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.LoopAgentServiceImpl;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.api.types.func.LoopPredicateIndex;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.fluent.func.FuncTaskItemListBuilder;
import io.serverlessworkflow.impl.TaskContext;

public class FlowLoopAgentService<T> extends LoopAgentServiceImpl<T> implements FlowAgentService<T> {

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
    private final WorkflowRegistry workflowRegistry;

    // We need our own copies (base fields are private)
    private int flowMaxIterations = Integer.MAX_VALUE;
    private BiPredicate<AgenticScope, Integer> flowExitCond = (scope, loopCounter) -> false;
    private boolean flowTestExitAtLoopEnd = false;

    protected FlowLoopAgentService(Class<T> agentServiceClass, Method agenticMethod, WorkflowRegistry workflowRegistry) {
        super(agentServiceClass, agenticMethod);
        this.workflowRegistry = workflowRegistry;
    }

    public static FlowLoopAgentService<UntypedAgent> builder(WorkflowRegistry workflowRegistry) {
        return new FlowLoopAgentService<>(UntypedAgent.class, null, workflowRegistry);
    }

    public static <T> FlowLoopAgentService<T> builder(Class<T> agentServiceClass, WorkflowRegistry workflowRegistry) {
        return new FlowLoopAgentService<>(agentServiceClass, validateAgentClass(agentServiceClass, false, LoopAgent.class),
                workflowRegistry);
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

    @Override
    public T build() {
        final FlowPlannerBuilder builder = new FlowPlannerBuilder(this);
        return build(builder::build);
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public WorkflowRegistry workflowRegistry() {
        return this.workflowRegistry;
    }

    @Override
    public Class<T> agentServiceClass() {
        return this.agentServiceClass;
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.LOOP;
    }

    @Override
    public Function<List<AgentInstance>, Consumer<FuncDoTaskBuilder>> tasksDefinition() {
        return (agents) -> tasks -> {
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
                            .tasks(forDo -> FlowAgentServiceUtil.addAgentTasks(forDo, agents))
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
