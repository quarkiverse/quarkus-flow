package io.quarkiverse.flow.langchain4j.workflow.service;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;
import io.quarkiverse.flow.langchain4j.workflow.flow.LoopAgenticFlow;

public class FlowLoopAgentInstance extends AbstractAgentInstance implements LoopAgentInstance {

    private final LoopAgenticFlow loopAgenticFlow;

    public FlowLoopAgentInstance(LoopAgenticFlow loopAgenticFlow, AgentInstance delegate) {
        super(delegate);
        this.loopAgenticFlow = loopAgenticFlow;
    }

    @Override
    public int maxIterations() {
        return loopAgenticFlow.maxIterations();
    }

    @Override
    public boolean testExitAtLoopEnd() {
        return loopAgenticFlow.testExitAtLoopEnd();
    }

    @Override
    public String exitCondition() {
        return loopAgenticFlow.exitConditionDescription();
    }
}
