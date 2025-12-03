package io.quarkiverse.flow.langchain4j.workflow;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkiverse.flow.internal.WorkflowInvocationMetadata;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkiverse.flow.langchain4j.schema.MethodInputJsonSchema;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Common Flow-backed implementation for LC4J workflow services
 * (sequential, parallel, conditional, loop, ...).
 * <p>
 * Subclasses are responsible only for:
 * - which LC4J service interface they implement (SequentialAgentService, ParallelAgentService, ...)
 * - how they wire AgentExecutors into FuncWorkflowBuilder in {@link #configureWorkflow(List)}.
 */
public abstract class AbstractFlowAgentService<T, S> extends AbstractService<T, S> {

    public static final String INVOKER_KIND_AGENTIC_LC4J = "agentic-lc4j";

    protected final FuncWorkflowBuilder workflowBuilder;
    protected final Method agenticMethod;

    protected AbstractFlowAgentService(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
        this.agenticMethod = agenticMethod;
        this.workflowBuilder = FuncWorkflowBuilder.workflow(agentServiceClass.getName());
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
    protected abstract void configureWorkflow(List<AgentExecutor> agentExecutors);

    @Override
    @SuppressWarnings("unchecked")
    public S subAgents(List<AgentExecutor> agentExecutors) {
        super.subAgents(agentExecutors);
        configureWorkflow(agentExecutors);
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public T build() {
        if (this.name != null && !this.name.isEmpty()) {
            workflowBuilder.document(d -> d.name(this.name).summary(this.description));
        }
        final Workflow workflow = workflowBuilder.build();

        if (agenticMethod != null) {
            WorkflowInvocationMetadata.setBeanInvoker(workflow, agentServiceClass, agenticMethod, INVOKER_KIND_AGENTIC_LC4J);
            MethodInputJsonSchema.applySchemaIfAbsent(workflow, agenticMethod);
        }

        final WorkflowDefinition wf = WorkflowRegistry.current().register(workflow, agentServiceClass.getName());

        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] { agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class },
                new FlowInvocationHandler(wf));
    }

    /**
     * Helper to wire a single AgentExecutor as a simple “call this agent with the scope”
     * task. Subclasses can use this for sequential style; others (parallel/conditional)
     * can compose differently.
     */
    protected void addSimpleAgentTask(AgentExecutor agentExecutor) {
        workflowBuilder.tasks(
                function(agentExecutor.agentInvoker().uniqueName(),
                        agentExecutor::execute,
                        DefaultAgenticScope.class)
                        .outputAs((out, wf, tf) -> agenticScopePassthrough(tf.rawInput())));
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
