package io.quarkiverse.flow.langchain4j.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final BlockingQueue<AgentExchange> agentExchangeQueue = new LinkedBlockingQueue<>();
    private final Map<String, AgentExchange> currentExchanges = new ConcurrentHashMap<>();

    private AtomicInteger parallelAgents = new AtomicInteger(0);

    private WorkflowDefinition definition;

    /**
     * Encapsulates the bidirectional exchange between workflow execution and planner actions.
     */
    private record AgentExchange(AgentInstance agent, CompletableFuture<Void> continuation) {
    }

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
        // Start workflow execution in background
        CompletableFuture.supplyAsync(() -> definition.instance(planningContext.agenticScope()).start().join())
                .thenRun(() -> executeAgent(null));

        return internalNextAction();
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        currentExchanges.remove(planningContext.previousAgentInvocation().agentId()).continuation.complete(null);
        return internalNextAction();
    }

    private Action internalNextAction() {
        if (parallelAgents.decrementAndGet() > 0) {
            return done();
        }

        List<AgentInstance> agents = new ArrayList<>();
        try {
            do {
                AgentExchange currentExchange = agentExchangeQueue.take();
                if (currentExchange.agent == null) {
                    LOG.info("Workflow terminated");
                    break;
                }
                currentExchanges.put(currentExchange.agent.agentId(), currentExchange);
                agents.add(currentExchange.agent);
            } while (!agentExchangeQueue.isEmpty());

            if (agents.size() > 1) {
                parallelAgents.set(agents.size());
            }

            LOG.info("Executing {} agent(s)", agents.size());
            return agents.isEmpty() ? done() : call(agents);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while waiting for agent exchange", e);
            return done();
        }
    }

    public CompletableFuture<Void> executeAgent(AgentInstance agent) {
        CompletableFuture<Void> continuation = new CompletableFuture<>();
        AgentExchange exchange = new AgentExchange(agent, continuation);

        try {
            agentExchangeQueue.put(exchange);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while queueing agent exchange", e);
            continuation.completeExceptionally(e);
        }

        return continuation;
    }
}
