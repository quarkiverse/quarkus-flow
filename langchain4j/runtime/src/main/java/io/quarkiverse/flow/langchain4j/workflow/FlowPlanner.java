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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;

public class FlowPlanner implements Planner, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPlanner.class);
    private final FlowAgentServiceWorkflowBuilder workflowBuilder;
    private final AgenticSystemTopology topology;
    private BlockingQueue<AgentExchange> agentExchangeQueue;
    private Map<String, AgentExchange> currentExchanges;
    private AtomicInteger parallelAgents;
    private AtomicBoolean closed;
    private WorkflowDefinition definition;
    private String workflowInstanceId;
    private Throwable terminationCause;

    public FlowPlanner(FlowAgentServiceWorkflowBuilder workflowBuilder, AgenticSystemTopology topology) {
        this.workflowBuilder = workflowBuilder;
        this.topology = topology;
    }

    @Override
    public AgenticSystemTopology topology() {
        return topology;
    }

    @Override
    public boolean terminated() {
        return closed.get();
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        // lazy creation since we are being instantiated twice upstream
        agentExchangeQueue = new LinkedBlockingQueue<>();
        currentExchanges = new ConcurrentHashMap<>();
        parallelAgents = new AtomicInteger(0);
        closed = new AtomicBoolean(false);
        definition = this.workflowBuilder.build(initPlanningContext.subagents());
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        final WorkflowInstance instance = definition.instance(planningContext.agenticScope());
        workflowInstanceId = instance.id();
        FlowPlannerSessions.getInstance().open(workflowInstanceId, this);

        // Starts workflow on a different thread
        // Despite returning a CompletableFuture, the start() method executes on the same thread by design.
        CompletableFuture.completedFuture(null)
                .thenComposeAsync(t -> instance.start())
                .whenCompleteAsync((r, e) -> {
                    if (e != null) {
                        LOG.error("Workflow failed", e);
                    }
                    signalTermination();
                    FlowPlannerSessions.getInstance().close(workflowInstanceId, e);
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
            return CompletableFuture.failedFuture(new CancellationException("Planner is closed"));
        }
        CompletableFuture<Void> continuation = new CompletableFuture<>();
        AgentExchange exchange = new AgentExchange(agent, continuation);

        try {
            agentExchangeQueue.put(exchange);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while queueing agent exchange", e);
            continuation.completeExceptionally(e);
            abort(e);
        }

        return continuation;
    }

    void doTermination(Throwable cause) {
        if (cause != null && terminationCause == null) {
            terminationCause = cause;
        }
    }

    public void close() {
        if (!closed.get()) {
            LOG.debug("Closing planner for workflow instance {}", this.workflowInstanceId);
            if (terminationCause != null)
                this.abort(terminationCause);
            else
                this.finish();
        }
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
        parallelAgents.set(0);
        this.signalTermination();
    }

    /**
     * Encapsulates the bidirectional exchange between workflow execution and planner actions.
     */
    private record AgentExchange(AgentInstance agent, CompletableFuture<Void> continuation) {
    }
}
