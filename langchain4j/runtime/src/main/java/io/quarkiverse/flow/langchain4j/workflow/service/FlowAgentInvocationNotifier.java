package io.quarkiverse.flow.langchain4j.workflow.service;

import jakarta.inject.Singleton;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import io.quarkiverse.flow.internal.FlowAgentInvocationListener;
import io.quarkiverse.flow.internal.FlowContextPropagator;
import io.quarkus.arc.Unremovable;

/**
 * Notifies {@link FlowAgentInvocationListener} when a leaf LangChain4j agent is invoked directly from a Flow task body
 * (the Java DSL's plain {@code function(..)} calling an {@code @Agent} bean, not the {@code agent(..)} helper, which is
 * already detected at build time), so integrations such as {@code quarkus-flow-opentelemetry} can type the enclosing
 * {@code task.execute} span without this module depending on them.
 * <p>
 * Composite agents are skipped: they become generated Flow workflows whose {@code workflow.execute} span is already
 * typed, so notifying for them here would be a duplicate.
 * <p>
 * Leaf sub-agents of a generated agentic workflow are skipped too: {@link FlowAgentContextListener} restores the
 * requesting task's context for those, since {@code FlowPlanner} runs them on a different thread, and notifies once that
 * context is current.
 * <p>
 * Registered with LangChain4j as a CDI {@link AgentListener} bean, which {@code AgenticRecorder} composes into every
 * agent it builds.
 */
@Singleton
@Unremovable
public class FlowAgentInvocationNotifier implements AgentListener {

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        if (!request.agent().leaf() || isDrivenByFlowPlanner(request)) {
            return;
        }

        FlowAgentInvocationListener.current().onAgentInvoked(request.agentName());
    }

    private static boolean isDrivenByFlowPlanner(AgentRequest request) {
        return request.agenticScope()
                .executionContext(
                        FlowAgentContextListener.contextKey(request.agentId())) instanceof FlowContextPropagator.Snapshot;
    }
}
