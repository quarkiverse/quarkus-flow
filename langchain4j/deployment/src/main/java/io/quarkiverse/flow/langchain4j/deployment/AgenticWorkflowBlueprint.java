package io.quarkiverse.flow.langchain4j.deployment;

import static io.quarkiverse.flow.langchain4j.deployment.AgentIdConstants.DESCRIPTION_ANNOTATION_VALUE;
import static io.quarkiverse.flow.langchain4j.deployment.AgentIdConstants.SUBAGENTS_ANNOTATION_VALUE;
import static io.quarkiverse.flow.langchain4j.deployment.Lc4jAnnotationScannerUtil.scanActivationConditions;
import static io.quarkiverse.flow.langchain4j.deployment.Lc4jAnnotationScannerUtil.scanLoopMetadata;
import static io.quarkiverse.flow.langchain4j.deployment.Lc4jAnnotations.CONDITIONAL_AGENT;
import static io.quarkiverse.flow.langchain4j.deployment.Lc4jAnnotations.LOOP_AGENT;
import static io.quarkiverse.flow.langchain4j.deployment.Lc4jAnnotations.PARALLEL_AGENT;
import static io.quarkiverse.flow.langchain4j.deployment.Lc4jAnnotations.SEQUENCE_AGENT;

import java.util.Arrays;
import java.util.List;

import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import io.quarkiverse.flow.langchain4j.annotations.ScheduleOn;

/**
 * Build-time representation of an agentic workflow extracted from LangChain4j annotations.
 * <p>
 * Contains topology information, sub-agents, and metadata needed to generate
 * the corresponding {@link io.quarkiverse.flow.langchain4j.workflow.AgenticFlow} class.
 */
record AgenticWorkflowBlueprint(String description,
        AgenticSystemTopology topology,
        List<Type> subAgents,
        ConditionalMetadata conditionalMetadata,
        LoopMetadata loopMetadata) {

    public AgenticWorkflowBlueprint(String description,
            AgenticSystemTopology topology,
            List<Type> subAgents) {
        this(description, topology, subAgents, null, null);
    }

    public AgenticWorkflowBlueprint(String description,
            AgenticSystemTopology topology,
            List<Type> subAgents,
            ConditionalMetadata conditionalMetadata) {
        this(description, topology, subAgents, conditionalMetadata, null);
    }

    public AgenticWorkflowBlueprint(String description,
            AgenticSystemTopology topology,
            List<Type> subAgents,
            LoopMetadata loopMetadata) {
        this(description, topology, subAgents, null, loopMetadata);
    }

    /**
     * Extracts agentic workflow blueprint from a method annotated with LangChain4j topology annotations.
     *
     * @param method the method to analyze
     * @return the workflow blueprint, or null if no topology annotation found
     */
    static AgenticWorkflowBlueprint fromAgenticMethod(MethodInfo method) {
        if (method.hasAnnotation(SEQUENCE_AGENT))
            return new AgenticWorkflowBlueprint(
                    getDescription(method, SEQUENCE_AGENT),
                    AgenticSystemTopology.SEQUENCE,
                    Arrays.stream(method.annotation(SEQUENCE_AGENT).value(SUBAGENTS_ANNOTATION_VALUE).asClassArray())
                            .toList());

        if (method.hasAnnotation(CONDITIONAL_AGENT))
            return new AgenticWorkflowBlueprint(
                    getDescription(method, CONDITIONAL_AGENT),
                    AgenticSystemTopology.ROUTER,
                    Arrays.stream(method.annotation(CONDITIONAL_AGENT).value(SUBAGENTS_ANNOTATION_VALUE).asClassArray())
                            .toList(),
                    scanActivationConditions(method.declaringClass()));

        if (method.hasAnnotation(LOOP_AGENT))
            return new AgenticWorkflowBlueprint(
                    getDescription(method, LOOP_AGENT),
                    AgenticSystemTopology.LOOP,
                    Arrays.stream(method.annotation(LOOP_AGENT).value(SUBAGENTS_ANNOTATION_VALUE).asClassArray()).toList(),
                    scanLoopMetadata(method.declaringClass(), method));

        if (method.hasAnnotation(PARALLEL_AGENT))
            return new AgenticWorkflowBlueprint(
                    getDescription(method, PARALLEL_AGENT),
                    AgenticSystemTopology.PARALLEL,
                    Arrays.stream(method.annotation(PARALLEL_AGENT).value(SUBAGENTS_ANNOTATION_VALUE).asClassArray())
                            .toList());

        return null;
    }

    static boolean isSchedulable(MethodInfo method) {
        return method.hasAnnotation(ScheduleOn.class);
    }

    /**
     * Safely extracts the description from an annotation, returning an empty string if not present.
     */
    private static String getDescription(MethodInfo method, DotName annotationType) {
        var annotation = method.annotation(annotationType);
        var descriptionValue = annotation.value(DESCRIPTION_ANNOTATION_VALUE);
        return descriptionValue != null ? descriptionValue.asString() : "";
    }

}
