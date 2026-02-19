package io.quarkiverse.flow.langchain4j.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import io.quarkus.arc.InstanceHandle;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;

@Dependent
public class FlowPlanner implements Planner, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPlanner.class);
    private final BlockingQueue<AgentExchange> agentExchangeQueue = new LinkedBlockingQueue<>();
    private final Map<String, AgentExchange> currentExchanges = new ConcurrentHashMap<>();
    private final AtomicInteger parallelAgents = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private FlowAgentServiceWorkflowBuilder workflowBuilder;
    private WorkflowDefinition definition;
    private InstanceHandle<FlowPlanner> selfHandle;
    private String workflowInstanceId;

    public FlowPlanner() {

    }

    void configure(FlowAgentServiceWorkflowBuilder workflowBuilder, InstanceHandle<FlowPlanner> plannerHandle) {
        this.workflowBuilder = workflowBuilder;
        this.selfHandle = plannerHandle;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        definition = this.workflowBuilder.build(initPlanningContext.subagents());
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        // Start workflow execution in background
        CompletableFuture
                .runAsync(() -> {
                    final WorkflowInstance instance = definition.instance(planningContext.agenticScope());
                    workflowInstanceId = instance.id();
                    FlowPlannerSessions.getInstance().open(workflowInstanceId, this, selfHandle);
                    instance.start().join();
                })
                .whenComplete((r, t) -> {
                    executeAgent(null);
                    FlowPlannerSessions.getInstance().close(workflowInstanceId, t);
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
        if (closed.get()) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new CancellationException("Planner is closed"));
            return f;
        }
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

    @PreDestroy
    public void close() {
        abort(new CancellationException("FlowPlanner destroyed"));
    }

    private void signalTermination() {
        agentExchangeQueue.offer(new AgentExchange(null, CompletableFuture.completedFuture(null)));
    }

    void abort(Throwable t) {
        if (!closed.compareAndSet(false, true))
            return;

        currentExchanges.values().forEach(ex -> ex.continuation.completeExceptionally(t));

        // drain
        AgentExchange ex;
        while ((ex = agentExchangeQueue.poll()) != null) {
            if (ex.agent != null)
                ex.continuation.completeExceptionally(t);
        }
        currentExchanges.clear();
        parallelAgents.set(0);

        this.signalTermination();
    }

    void finish() {
        if (!closed.compareAndSet(false, true))
            return;

        // best-effort cleanup
        currentExchanges.clear();
        agentExchangeQueue.clear();
        parallelAgents.set(0);

        this.signalTermination();
    }

    /**
     * Encapsulates the bidirectional exchange between workflow execution and planner actions.
     */
    private record AgentExchange(AgentInstance agent, CompletableFuture<Void> continuation) {
    }
}
