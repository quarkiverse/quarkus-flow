package io.quarkiverse.flow.langchain4j.workflow.service;

import jakarta.inject.Singleton;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.flow.internal.FlowAgentInvocationListener;
import io.quarkiverse.flow.internal.FlowContextPropagator;
import io.quarkiverse.flow.langchain4j.FlowAgentListener;
import io.quarkus.arc.Unremovable;

/**
 * Notifies {@link FlowAgentInvocationListener} when a leaf LangChain4j agent is invoked directly from a Flow task body
 * (the Java DSL's plain {@code function(..)} calling an {@code @Agent} bean, not the {@code agent(..)} helper, which is
 * already detected at build time), so integrations such as {@code quarkus-flow-opentelemetry} can type the enclosing
 * {@code task.execute} span without this module depending on them.
 * <p>
 * Composite agents are skipped: they become generated Flow workflows whose {@code workflow.execute} span is already
 * typed, so notifying for them here would be a duplicate. {@link FlowAgentContextListener} notifies the
 * {@link FlowAgentListener} beans of them.
 * <p>
 * It also notifies the {@link FlowAgentListener} beans of the same leaf agents, on the thread that invoked the agent.
 * <p>
 * Leaf sub-agents of a generated agentic workflow are skipped for both: {@link FlowAgentContextListener} restores the
 * requesting task's context for those, since {@code FlowPlanner} runs them on a different thread, and notifies once that
 * context is current.
 * <p>
 * Registered with LangChain4j as a CDI {@link AgentListener} bean, which {@code AgenticRecorder} composes into every
 * agent it builds.
 */
@Singleton
@Unremovable
public class FlowAgentInvocationNotifier implements AgentListener {

    private final FlowAgentListenerDispatcher dispatcher;

    FlowAgentInvocationNotifier(FlowAgentListenerDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        if (!isLeafNotDrivenByFlowPlanner(request.agenticScope(), request.agent())) {
            return;
        }

        FlowAgentInvocationListener.current().onAgentInvoked(request.agentName());
        dispatcher.beforeAgentInvocation(request);
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        if (isLeafNotDrivenByFlowPlanner(response.agenticScope(), response.agent())) {
            dispatcher.afterAgentInvocation(response);
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        if (isLeafNotDrivenByFlowPlanner(error.agenticScope(), error.agent())) {
            dispatcher.onAgentInvocationError(error);
        }
    }

    private static boolean isLeafNotDrivenByFlowPlanner(AgenticScope agenticScope, AgentInstance agent) {
        return agent.leaf() && !(agenticScope.executionContext(
                FlowAgentContextListener.contextKey(agent.agentId())) instanceof FlowContextPropagator.Snapshot);
    }
}
