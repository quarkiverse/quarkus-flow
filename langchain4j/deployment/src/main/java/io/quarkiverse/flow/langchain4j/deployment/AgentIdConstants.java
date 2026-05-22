package io.quarkiverse.flow.langchain4j.deployment;

/**
 * Constants for agent ID generation and annotation value names.
 */
final class AgentIdConstants {

    private AgentIdConstants() {
        // Prevent instantiation
    }

    /**
     * Annotation value name for subAgents array in topology annotations.
     */
    static final String SUBAGENTS_ANNOTATION_VALUE = "subAgents";

    /**
     * Annotation value name for description in topology annotations.
     */
    static final String DESCRIPTION_ANNOTATION_VALUE = "description";
}
