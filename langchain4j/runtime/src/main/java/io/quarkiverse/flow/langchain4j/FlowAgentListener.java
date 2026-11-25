package io.quarkiverse.flow.langchain4j;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;

/**
 * Listens to LangChain4j agent invocations, with the context of the Quarkus Flow task that requested the agent current.
 * <p>
 * The sub-agents of a generated agentic workflow ({@code @SequenceAgent}, {@code @ParallelAgent},
 * {@code @ConditionalAgent}, {@code @LoopAgent}) run on the planner thread or on LangChain4j's executor, not on the
 * thread of the workflow task that requested them. Quarkus Flow restores that task's context, such as its OpenTelemetry
 * {@code task.execute} span, for the duration of each invocation. A plain {@link AgentListener} can't rely on that
 * context, because LangChain4j doesn't define the order in which it notifies listeners. Quarkus Flow calls a
 * {@code FlowAgentListener} itself, after it has made the task's context current and before it restores the previous
 * one, so the context is current in every callback, whatever the order of the other listeners.
 * <p>
 * Every leaf agent invocation in the application is reported, and every invocation of an agentic system that Quarkus
 * Flow builds, whether it is generated from an annotation or built with {@code FlowAgentsBuilderService}. An agent that
 * Quarkus Flow doesn't hand to another thread, such as the root agentic system or a leaf agent invoked from a task body,
 * sees the context of the thread that invoked it, as a plain {@link AgentListener} does. Inside a task body, that's the
 * task's own context.
 * <p>
 * Implementations are CDI beans of any scope. They are called in the order of their {@code jakarta.annotation.Priority},
 * highest first. An exception thrown by an implementation is logged and doesn't stop the other implementations, the
 * agent invocation, or Quarkus Flow restoring the previous context.
 * <p>
 * Don't also implement {@link AgentListener} in the same bean: Quarkus LangChain4j registers every {@code AgentListener}
 * bean on every agent, so each callback would also be called directly by LangChain4j, outside the task's context. For
 * the callbacks this interface doesn't cover, such as {@link AgentListener#onAgenticSystemSuspended}, use a separate
 * {@code AgentListener}.
 */
public interface FlowAgentListener {

    /**
     * Called before an agent is invoked.
     *
     * @param request the agent invocation, with its inputs; never {@code null}
     */
    default void beforeAgentInvocation(AgentRequest request) {
    }

    /**
     * Called after an agent invocation completes, while the context it ran in is still current.
     * <p>
     * Not called for the agents left running when an agentic system suspends, as for
     * {@link AgentListener#afterAgentInvocation(AgentResponse)}.
     *
     * @param response the completed agent invocation, with its output; never {@code null}
     */
    default void afterAgentInvocation(AgentResponse response) {
    }

    /**
     * Called when an agent invocation fails, while the context it ran in is still current.
     *
     * @param error the failed agent invocation, with its cause; never {@code null}
     */
    default void onAgentInvocationError(AgentInvocationError error) {
    }
}
