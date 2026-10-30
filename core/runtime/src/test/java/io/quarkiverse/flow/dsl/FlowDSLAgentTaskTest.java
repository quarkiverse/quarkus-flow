package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.agent;
import static io.quarkiverse.flow.dsl.FlowDSL.withUniqueId;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.types.UniqueIdBiFunction;
import io.quarkiverse.flow.internal.WorkflowAgentMetadata;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskMetadata;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Verifies that {@code agent(..)} marks its task as an agent invocation, while {@code withUniqueId(..)} does not.
 */
class FlowDSLAgentTaskTest {

    private static final UniqueIdBiFunction<String, String> FN = (uniqueId, payload) -> payload;

    @Test
    @DisplayName("test_agent_task_is_marked_as_agent_invocation")
    void test_agent_task_is_marked_as_agent_invocation() {
        Workflow wf = FlowWorkflowBuilder.workflow("wf-agent-task")
                .tasks(agent("analyst", FN, String.class),
                        withUniqueId("plain", FN, String.class),
                        agent(FN, String.class))
                .build();

        assertThat(wf.getDo())
                .extracting(item -> WorkflowAgentMetadata.isAgentTask(task(wf, item.getName())))
                .containsExactly(true, false, true);
    }

    @Test
    @DisplayName("test_agent_marker_keeps_existing_task_metadata")
    void test_agent_marker_keeps_existing_task_metadata() {
        var task = new CallFunction()
                .withMetadata(new TaskMetadata().withAdditionalProperty("some.key", "value"));

        WorkflowAgentMetadata.markAgentTask(task);

        assertThat(task.getMetadata().getAdditionalProperties())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "some.key", "value",
                        WorkflowAgentMetadata.META_AGENT_TASK, true));
    }

    @Test
    @DisplayName("test_is_agent_task_is_false_without_marker")
    void test_is_agent_task_is_false_without_marker() {
        assertThat(WorkflowAgentMetadata.isAgentTask(null))
                .isFalse();
        assertThat(WorkflowAgentMetadata.isAgentTask(new CallFunction()))
                .isFalse();
        assertThat(WorkflowAgentMetadata.isAgentTask(new CallFunction()
                .withMetadata(new TaskMetadata().withAdditionalProperty(WorkflowAgentMetadata.META_AGENT_TASK, "true"))))
                .as("only a boolean true marks the task")
                .isFalse();
    }

    private static TaskBase task(Workflow wf, String name) {
        return wf.getDo()
                .stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .map(item -> (TaskBase) item.getTask().getCallTask().get())
                .orElseThrow();
    }
}
