package io.quarkiverse.flow.langchain4j.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import io.serverlessworkflow.impl.WorkflowDefinition;

public class FlowPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPlanner.class);

    private final FlowAgentServiceWorkflowBuilder workflowBuilder;

    private final BlockingQueue<AgentExchange> agentExchangeQueue = new LinkedBlockingQueue<>();
    private final Map<String, AgentExchange> currentExchanges = new ConcurrentHashMap<>();

    private final AtomicInteger parallelAgents = new AtomicInteger(0);

    private WorkflowDefinition definition;
    private String sessionId;

    public FlowPlanner(FlowAgentServiceWorkflowBuilder workflowBuilder) {
        this.workflowBuilder = workflowBuilder;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        definition = this.workflowBuilder.build(initPlanningContext.subagents(), initPlanningContext.plannerAgent().agentId());
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        sessionId = FlowPlannerSessions.INSTANCE.register(planningContext.agenticScope(), this);
        // Start workflow execution in background
        CompletableFuture
                .runAsync(() -> definition.instance(planningContext.agenticScope()).start().join())
                .whenComplete((r, t) -> {
                    executeAgent(null);
                    if (t != null)
                        LOG.error("Workflow failed", t);
                });

        return internalNextAction();
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgentExchange exchange = currentExchanges.remove(planningContext.previousAgentInvocation().agentId());
        if (exchange != null)
            exchange.continuation.complete(null);
        else
            LOG.warn("No exchange found for agent {}", planningContext.previousAgentInvocation().agentId());

        int remaining = parallelAgents.decrementAndGet();
        if (remaining > 0) {
            return done();
        }

        return internalNextAction();
    }

    private Action internalNextAction() {
        List<AgentInstance> agents = new ArrayList<>();
        try {
            do {
                AgentExchange currentExchange = agentExchangeQueue.take();
                if (currentExchange.agent == null) {
                    LOG.debug("Workflow terminated");
                    cleanUp();
                    break;
                }
                currentExchanges.put(currentExchange.agent.agentId(), currentExchange);
                agents.add(currentExchange.agent);
            } while (!agentExchangeQueue.isEmpty());

            parallelAgents.set(agents.size());

            LOG.debug("Executing {} agent(s)", agents.size());
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

    private void cleanUp() {
        this.currentExchanges.clear();
        this.agentExchangeQueue.clear();
        this.parallelAgents.set(0);
        FlowPlannerSessions.INSTANCE.unregister(sessionId, this);
    }

    /**
     * Encapsulates the bidirectional exchange between workflow execution and planner actions.
     */
    private record AgentExchange(AgentInstance agent, CompletableFuture<Void> continuation) {
    }
}
