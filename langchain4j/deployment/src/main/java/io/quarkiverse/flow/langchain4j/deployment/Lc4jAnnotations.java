package io.quarkiverse.flow.langchain4j.deployment;

import org.jboss.jandex.DotName;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;

/**
 * LangChain4j annotation DotNames used during Jandex scanning.
 * <p>
 * This class centralizes all annotation references to avoid duplication across the deployment module.
 */
final class Lc4jAnnotations {

    private Lc4jAnnotations() {
        // Prevent instantiation
    }

    // Agent annotations
    static final DotName AGENT = DotName.createSimple(Agent.class.getName());
    static final DotName SEQUENCE_AGENT = DotName.createSimple(SequenceAgent.class.getName());
    static final DotName PARALLEL_AGENT = DotName.createSimple(ParallelAgent.class.getName());
    static final DotName PARALLEL_MAPPER_AGENT = DotName.createSimple(ParallelMapperAgent.class.getName());
    static final DotName LOOP_AGENT = DotName.createSimple(LoopAgent.class.getName());
    static final DotName CONDITIONAL_AGENT = DotName.createSimple(ConditionalAgent.class.getName());
    static final DotName SUPERVISOR_AGENT = DotName.createSimple(SupervisorAgent.class.getName());
    static final DotName PLANNER_AGENT = DotName.createSimple(PlannerAgent.class.getName());
    static final DotName A2A_AGENT = DotName.createSimple(A2AClientAgent.class.getName());
    static final DotName MCP_AGENT = DotName.createSimple(McpClientAgent.class.getName());

    /**
     * All agent annotations for iteration.
     * Ordered by most common usage first for optimization.
     */
    static final DotName[] ALL_AGENT_ANNOTATIONS = {
            AGENT,
            SEQUENCE_AGENT,
            PARALLEL_AGENT,
            PARALLEL_MAPPER_AGENT,
            LOOP_AGENT,
            CONDITIONAL_AGENT,
            SUPERVISOR_AGENT,
            PLANNER_AGENT,
            A2A_AGENT,
            MCP_AGENT
    };
}
