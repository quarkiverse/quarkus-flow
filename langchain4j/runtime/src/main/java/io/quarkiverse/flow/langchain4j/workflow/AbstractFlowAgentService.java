package io.quarkiverse.flow.langchain4j.workflow;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Common Flow-backed implementation for LC4J workflow services
 * (sequential, parallel, conditional, loop, ...).
 * <p>
 * Subclasses are responsible only for:
 * - which LC4J service interface they implement (SequentialAgentService, ParallelAgentService, ...)
 * - how they wire AgentExecutors into FuncWorkflowBuilder in {@link #doWorkflowTasks(List)}.
 */
public abstract class AbstractFlowAgentService<T, S> extends AbstractService<T, S> {

    public static final String INVOKER_KIND_AGENTIC_LC4J = "agentic-lc4j";
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFlowAgentService.class.getName());

    protected final Method agenticMethod;

    protected AbstractFlowAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
        this.agenticMethod = agenticMethod;
    }

    /**
     * Helper for the very common “AgenticScope passthrough” output mapping.
     * Reuse this in all Flow-*AgentService implementations that
     * want to keep AgenticScope as the thread of data between tasks.
     */
    protected static Object agenticScopePassthrough(WorkflowModel rawInput) {
        Object raw = rawInput.asJavaObject();
        if (raw instanceof AgenticScope scope) {
            return scope;
        }
        throw new IllegalStateException("Expected AgenticScope but got " + raw);
    }

    /**
     * Called when subAgents are provided. Implementations should map each AgentExecutor
     * to the appropriate FuncWorkflowBuilder structure (sequence, parallel, conditional, loop, ...).
     */
    protected abstract Consumer<FuncDoTaskBuilder> doWorkflowTasks(List<AgentExecutor> agentExecutors);

    @SuppressWarnings("unchecked")
    public T build() {
        final WorkflowDefinitionId id = WorkflowNameUtils.newId(this.agentServiceClass);
        final WorkflowRegistry registry = WorkflowRegistry.current();

        FuncWorkflowBuilder builder = FuncWorkflowBuilder.workflow();
        builder.document(d -> d
                .name(id.name())
                .namespace(id.namespace())
                .version(id.version())
                .summary(this.description));
        builder.tasks(doWorkflowTasks(super.agentExecutors()));

        final Workflow topologyWorkflow = builder.build();
        Workflow workflowToRegister = registry.lookupDescriptor(id)
                .map(descriptor -> {
                    descriptor.getDocument().setSummary(this.description);
                    descriptor.setDo(topologyWorkflow.getDo());
                    return descriptor;
                })
                .orElse(topologyWorkflow);

        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] { agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class },
                new FlowInvocationHandler(registry.register(workflowToRegister)));
    }

    /**
     * Common invocation handler for all Flow-backed services.
     * The topology semantics (sequential/parallel/…) are encoded in the built WorkflowDefinition,
     * so the runtime here is identical for all patterns.
     */
    private class FlowInvocationHandler extends AbstractAgentInvocationHandler {

        private final WorkflowDefinition workflowDefinition;

        FlowInvocationHandler(WorkflowDefinition workflowDefinition) {
            super(AbstractFlowAgentService.this);
            this.workflowDefinition = workflowDefinition;
        }

        FlowInvocationHandler(DefaultAgenticScope agenticScope, WorkflowDefinition workflowDefinition) {
            super(AbstractFlowAgentService.this, agenticScope);
            this.workflowDefinition = workflowDefinition;
        }

        @Override
        protected Object doAgentAction(DefaultAgenticScope agenticScope) {
            // If no sub-agents, mirror LC4J semantics: just apply output on the scope
            if (agentExecutors().isEmpty()) {
                LOGGER.warn("No workflow executors configured for workflow '{}'(doc='{}'). Skipping workflow execution.",
                        this.name,
                        workflowDefinition.workflow().getDocument().getName());
                return result(agenticScope, output.apply(agenticScope));
            }

            // All sequencing/parallelism/conditions are encoded in the workflow definition
            this.workflowDefinition.instance(agenticScope).start().join();
            return result(agenticScope, output.apply(agenticScope));
        }

        @Override
        protected InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope) {
            return new FlowInvocationHandler(agenticScope, workflowDefinition);
        }
    }
}
