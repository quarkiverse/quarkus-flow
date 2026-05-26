package io.quarkiverse.flow.langchain4j.workflow.runtime;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Predicate;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.impl.ConditionalAgentServiceImpl;
import io.quarkiverse.flow.langchain4j.workflow.service.*;

public final class RuntimeFlowConditionalAgentService<T> extends FlowConditionalAgentService<T> {

    public RuntimeFlowConditionalAgentService(Class<T> agentServiceClass, Method agenticMethod,
            RuntimeConditionalAgenticFlow flow) {
        super(agentServiceClass, agenticMethod, flow);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Object... agents) {
        // No predicate - defaults to always-true condition
        for (Object agent : agents) {
            ((RuntimeConditionalAgenticFlow) flow).addSubAgentWithPredicate(scope -> true, agent);
        }
        return super.subAgents(agents);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Collection<?> agents) {
        // NOTE: Don't add task names here! This method is called by the parent LC4j class
        // as a callback from the varargs overload. Adding task names here would duplicate them.
        return super.subAgents(agents);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Predicate<AgenticScope> activationPredicate, Object... agents) {
        for (Object agent : agents) {
            ((RuntimeConditionalAgenticFlow) flow).addSubAgentWithPredicate(activationPredicate, agent);
        }
        return super.subAgents(activationPredicate, agents);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(
            String conditionDescription, Predicate<AgenticScope> activationPredicate, Object... agents) {
        // Description is passed to parent but we still need to track in our flow
        for (Object agent : agents) {
            ((RuntimeConditionalAgenticFlow) flow).addSubAgentWithPredicate(activationPredicate, agent);
        }
        return super.subAgents(conditionDescription, activationPredicate, agents);
    }

    @Override
    public T build() {
        ((RuntimeConditionalAgenticFlow) flow).init();
        return super.build();
    }
}
