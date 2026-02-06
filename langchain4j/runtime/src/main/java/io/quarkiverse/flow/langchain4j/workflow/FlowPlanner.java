package io.quarkiverse.flow.langchain4j.workflow;

import java.util.function.Consumer;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.ChatMemoryAccessProvider;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class FlowPlanner implements Planner, ChatMemoryAccessProvider {

    private final Class<?> agentServiceClass;
    private final String description;
    private final Consumer<FuncDoTaskBuilder> tasks;

    private WorkflowDefinition definition;

    public FlowPlanner(Class<?> agentServiceClass, String description, Consumer<FuncDoTaskBuilder> tasks) {
        this.agentServiceClass = agentServiceClass;
        this.description = description;
        this.tasks = tasks;
    }

    private static WorkflowDefinition buildWorkflow(Class<?> agentServiceClass, String description,
            Consumer<FuncDoTaskBuilder> tasks) {
        final WorkflowDefinitionId id = WorkflowNameUtils.newId(agentServiceClass);
        final WorkflowRegistry registry = WorkflowRegistry.current();

        FuncWorkflowBuilder builder = FuncWorkflowBuilder.workflow();
        builder.document(d -> d
                .name(id.name())
                .namespace(id.namespace())
                .version(id.version())
                .summary(description));
        builder.tasks(tasks);

        final Workflow topologyWorkflow = builder.build();
        Workflow workflowToRegister = registry.lookupDescriptor(id)
                .map(descriptor -> {
                    descriptor.getDocument().setName(id.name());
                    descriptor.getDocument().setNamespace(id.namespace());
                    descriptor.getDocument().setVersion(id.version());
                    descriptor.getDocument().setSummary(description);
                    descriptor.setDo(topologyWorkflow.getDo());
                    return descriptor;
                })
                .orElse(topologyWorkflow);

        return registry.register(workflowToRegister);
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        if (definition == null) {
            definition = buildWorkflow(agentServiceClass, description, tasks);
        }
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        // guardrail
        WorkflowDefinition def = definition;
        if (def == null) {
            def = buildWorkflow(agentServiceClass, description, tasks);
            definition = def;
        }

        // All sequencing/parallelism/conditions are encoded in the workflow definition
        def.instance(planningContext.agenticScope()).start().join();

        // We've executed the workflow definition to this point, the lc4j agentic engine can
        // skip their agents call to this point and just wrap up and return the result.
        return done();
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return done();
    }

    @Override
    public ChatMemoryAccess chatMemoryAccess(AgenticScope agenticScope) {
        // TODO: should the planner also have the ChatMemoryAccess responsibility? If so, how can we create this object from here?
        throw new UnsupportedOperationException(
                "ChatMemoryAccess is not supported by Quarkus Flow agentic integration. " +
                        "If you are using supervisor/memory features, use a LangChain4j Agentic planner implementation instead.");
    }
}
