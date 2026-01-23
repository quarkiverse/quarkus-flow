package io.quarkiverse.flow.langchain4j.workflow;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncDoTaskBuilder;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class FlowPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPlanner.class);

    private final Class<?> agentServiceClass;
    private final String description;
    private final BiFunction<FlowPlanner, InitPlanningContext, Consumer<FuncDoTaskBuilder>> tasks;

    private CompletableFuture<List<AgentInstance>> nextAgentFuture;
    private CompletableFuture<Void> nextActionFuture;

    private WorkflowDefinition definition;

    public FlowPlanner(Class<?> agentServiceClass, String description,
            BiFunction<FlowPlanner, InitPlanningContext, Consumer<FuncDoTaskBuilder>> tasks) {
        this.agentServiceClass = agentServiceClass;
        this.description = description;
        this.tasks = tasks;
    }

    private WorkflowDefinition buildWorkflow(InitPlanningContext initPlanningContext) {
        final WorkflowDefinitionId id = WorkflowNameUtils.newId(agentServiceClass);
        final WorkflowRegistry registry = WorkflowRegistry.current();

        FuncWorkflowBuilder builder = FuncWorkflowBuilder.workflow();
        builder.document(d -> d
                .name(id.name())
                .namespace(id.namespace())
                .version(id.version())
                .summary(description));
        builder.tasks(tasks.apply(this, initPlanningContext));

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

        LOG.info("Building LC4J Workflow {}", workflowToRegister.getDocument().getName());
        return registry.register(workflowToRegister);
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        definition = buildWorkflow(initPlanningContext);
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        nextAgentFuture = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> definition.instance(planningContext.agenticScope()).start().join())
                .thenRun(() -> executeAgents(null));

        final List<AgentInstance> agents = nextAgentFuture.join();
        if (agents == null || agents.isEmpty()) {
            return done();
        }
        return call(agents);
    }

    public CompletableFuture<Void> executeAgents(List<AgentInstance> agents) {
        nextAgentFuture.complete(agents);
        nextActionFuture = new CompletableFuture<>();
        return nextActionFuture;
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        nextAgentFuture = new CompletableFuture<>();
        nextActionFuture.complete(null);
        List<AgentInstance> nextAgents = nextAgentFuture.join();
        return nextAgents == null ? done() : call(nextAgents);
    }
}
