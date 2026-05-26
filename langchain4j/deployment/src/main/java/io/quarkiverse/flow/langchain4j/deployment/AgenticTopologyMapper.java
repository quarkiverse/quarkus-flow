package io.quarkiverse.flow.langchain4j.deployment;

import java.util.Map;

import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import io.quarkiverse.flow.langchain4j.workflow.flow.AgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.flow.ConditionalAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.flow.LoopAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.flow.ParallelAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.flow.SequentialAgenticFlow;

/**
 * Maps LangChain4j {@link AgenticSystemTopology} to corresponding {@link AgenticFlow} implementation classes.
 */
final class AgenticTopologyMapper {

    private AgenticTopologyMapper() {
        // Prevent instantiation
    }

    private static final Map<AgenticSystemTopology, Class<? extends AgenticFlow>> TOPOLOGY_MAP = Map.of(
            AgenticSystemTopology.PARALLEL, ParallelAgenticFlow.class,
            AgenticSystemTopology.LOOP, LoopAgenticFlow.class,
            AgenticSystemTopology.ROUTER, ConditionalAgenticFlow.class,
            AgenticSystemTopology.SEQUENCE, SequentialAgenticFlow.class);

    /**
     * Returns the {@link AgenticFlow} implementation class for the given topology.
     *
     * @param topology the agentic system topology
     * @return the corresponding flow class
     * @throws IllegalArgumentException if the topology is not supported
     */
    static Class<? extends AgenticFlow> getFlowClass(AgenticSystemTopology topology) {
        Class<? extends AgenticFlow> flowClass = TOPOLOGY_MAP.get(topology);
        if (flowClass == null) {
            throw new IllegalArgumentException(
                    "Unsupported AgenticSystemTopology: " + topology +
                            ". Supported topologies: " + TOPOLOGY_MAP.keySet());
        }
        return flowClass;
    }
}
