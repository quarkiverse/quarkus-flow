package io.quarkiverse.flow.internal;

import static java.util.function.Predicate.not;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskMetadata;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.api.types.WorkflowMetadata;
import io.serverlessworkflow.fluent.spec.DocumentBuilder;

/**
 * Helper for storing and reading "agent" metadata on a workflow document.
 * <p>
 * A workflow whose document metadata carries {@link #META_AGENT_NAME} is an agent invocation. Generated agentic
 * workflows set it in every launch mode, and any other workflow may set it to opt in. Integrations such as
 * OpenTelemetry use it to type the workflow according to the GenAI semantic conventions.
 * <p>
 * A single task can also be an agent invocation: tasks created with the Java DSL
 * {@link io.quarkiverse.flow.dsl.FlowDSL#agent(String, io.quarkiverse.flow.dsl.types.UniqueIdBiFunction) agent(..)}
 * carry {@link #META_AGENT_TASK} in their task metadata, and the task name is the agent name.
 * <p>
 * Metadata contract:
 * <ul>
 * <li>{@code META_AGENT_NAME} – workflow document metadata: non-blank name of the agent the workflow invokes</li>
 * <li>{@code META_AGENT_TASK} – task metadata: {@code true} when the task is an agent invocation</li>
 * </ul>
 */
public final class WorkflowAgentMetadata {

    /**
     * Name of the agent the workflow invokes. Its presence marks the workflow as an agent invocation.
     */
    public static final String META_AGENT_NAME = "io.quarkiverse.flow.agent.name";

    /**
     * Task metadata flag marking the task as an agent invocation. The task name is the agent name.
     */
    public static final String META_AGENT_TASK = "io.quarkiverse.flow.agent.task";

    private WorkflowAgentMetadata() {
    }

    /**
     * Creates a Consumer that marks a workflow as an invocation of the given agent.
     *
     * @param agentName non-blank agent name
     * @return Consumer that sets the metadata
     */
    public static Consumer<DocumentBuilder.WorkflowMetadataBuilder> agentMetadata(String agentName) {
        Objects.requireNonNull(agentName, "agentName must not be null");
        if (agentName.isBlank()) {
            throw new IllegalArgumentException("agentName must not be blank");
        }
        return m -> m.metadata(META_AGENT_NAME, agentName);
    }

    /**
     * Returns the agent name of the given workflow, if its document metadata marks it as an agent invocation.
     *
     * @param workflow the workflow, may be {@code null}
     * @return the non-blank agent name, or empty if the workflow is not an agent invocation
     */
    public static Optional<String> agentNameOf(Workflow workflow) {
        return Optional.ofNullable(workflow)
                .map(Workflow::getDocument)
                .map(Document::getMetadata)
                .map(WorkflowMetadata::getAdditionalProperties)
                .map(properties -> properties.get(META_AGENT_NAME))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(not(String::isBlank));
    }

    /**
     * Marks the given task as an agent invocation, keeping any task metadata already present.
     *
     * @param task the task to mark
     */
    public static void markAgentTask(TaskBase task) {
        Objects.requireNonNull(task, "task must not be null");
        TaskMetadata metadata = Optional.ofNullable(task.getMetadata())
                .orElseGet(TaskMetadata::new);
        metadata.withAdditionalProperty(META_AGENT_TASK, Boolean.TRUE);
        task.setMetadata(metadata);
    }

    /**
     * Returns whether the given task is an agent invocation, i.e. its task metadata carries {@link #META_AGENT_TASK}
     * set to {@code true}.
     *
     * @param task the task, may be {@code null}
     * @return {@code true} if the task is an agent invocation
     */
    public static boolean isAgentTask(TaskBase task) {
        return Optional.ofNullable(task)
                .map(TaskBase::getMetadata)
                .map(TaskMetadata::getAdditionalProperties)
                .map(properties -> properties.get(META_AGENT_TASK))
                .filter(Boolean.TRUE::equals)
                .isPresent();
    }
}
