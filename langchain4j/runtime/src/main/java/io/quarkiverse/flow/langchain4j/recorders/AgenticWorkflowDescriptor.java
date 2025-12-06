package io.quarkiverse.flow.langchain4j.recorders;

import java.util.List;

/**
 * Runtime descriptor for an LC4J agentic workflow.
 * This is the type that the recorder uses – it must live in the runtime module.
 */
public record AgenticWorkflowDescriptor(String ifaceName,
        String methodName,
        List<String> parameterTypeNames) {

    public AgenticWorkflowDescriptor {
        // Defensive copy so we don't accidentally mutate it later
        parameterTypeNames = List.copyOf(parameterTypeNames);
    }
}
