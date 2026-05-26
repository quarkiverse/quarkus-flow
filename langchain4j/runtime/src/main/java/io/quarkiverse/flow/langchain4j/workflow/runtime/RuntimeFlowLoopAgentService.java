package io.quarkiverse.flow.langchain4j.workflow.runtime;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.impl.LoopAgentServiceImpl;
import io.quarkiverse.flow.langchain4j.workflow.service.*;

public final class RuntimeFlowLoopAgentService<T> extends FlowLoopAgentService<T> {

    public RuntimeFlowLoopAgentService(Class<T> agentServiceClass, Method agenticMethod, RuntimeLoopAgenticFlow flow) {
        super(agentServiceClass, agenticMethod, flow);
    }

    @Override
    public LoopAgentService<T> subAgents(Object... agents) {
        ((RuntimeLoopAgenticFlow) flow).addSubAgentTaskName(agents);
        return super.subAgents(agents);
    }

    @Override
    public LoopAgentService<T> subAgents(Collection<?> agents) {
        // NOTE: Don't add task names here! This method is called by the parent LC4j class
        // as a callback from the varargs overload. Adding task names here would duplicate them.
        return super.subAgents(agents);
    }

    @Override
    public LoopAgentServiceImpl<T> maxIterations(int maxIterations) {
        ((RuntimeLoopAgenticFlow) flow).setMaxIterations(maxIterations);
        return super.maxIterations(maxIterations);
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(Predicate<AgenticScope> exitCondition) {
        // Convert Predicate to BiPredicate (ignoring loop counter)
        ((RuntimeLoopAgenticFlow) flow).setExitCondition((scope, loopCounter) -> exitCondition.test(scope));
        return super.exitCondition(exitCondition);
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition) {
        ((RuntimeLoopAgenticFlow) flow).setExitCondition(exitCondition);
        return super.exitCondition(exitCondition);
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(
            String exitConditionDescription, Predicate<AgenticScope> exitCondition) {
        // Convert Predicate to BiPredicate (ignoring loop counter)
        // Description is passed to parent but we still need to track in our flow
        ((RuntimeLoopAgenticFlow) flow).setExitCondition((scope, loopCounter) -> exitCondition.test(scope));
        return super.exitCondition(exitConditionDescription, exitCondition);
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(
            String exitConditionDescription, BiPredicate<AgenticScope, Integer> exitCondition) {
        // Description is passed to parent but we still need to track in our flow
        ((RuntimeLoopAgenticFlow) flow).setExitCondition(exitCondition);
        return super.exitCondition(exitConditionDescription, exitCondition);
    }

    @Override
    public LoopAgentServiceImpl<T> testExitAtLoopEnd(boolean testExitAtLoopEnd) {
        ((RuntimeLoopAgenticFlow) flow).setTestExitAtLoopEnd(testExitAtLoopEnd);
        return super.testExitAtLoopEnd(testExitAtLoopEnd);
    }

    @Override
    public T build() {
        ((RuntimeLoopAgenticFlow) flow).init();
        return super.build();
    }
}
