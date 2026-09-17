package io.quarkiverse.flow.langchain4j.workflow.service;

import java.util.List;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.workflow.ConditionalAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import io.quarkiverse.flow.langchain4j.workflow.flow.ConditionalAgenticFlow;

public class FlowConditionalAgentInstance extends AbstractAgentInstance implements ConditionalAgentInstance {

    private final ConditionalAgenticFlow flow;

    public FlowConditionalAgentInstance(final ConditionalAgenticFlow agenticFlow, final AgentInstance agentInstance) {
        super(agentInstance);
        this.flow = agenticFlow;
    }

    @Override
    public List<ConditionalAgent> conditionalSubagents() {
        return this.flow.conditionalAgents();
    }
}
