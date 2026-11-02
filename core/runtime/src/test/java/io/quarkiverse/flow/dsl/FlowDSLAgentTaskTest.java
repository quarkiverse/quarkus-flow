package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.agent;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.withUniqueId;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.executors.JavaCallFunctionBuilder;
import io.quarkiverse.flow.dsl.executors.JavaFilterFunctionCallExecutor;
import io.quarkiverse.flow.dsl.types.AgentFunction;
import io.quarkiverse.flow.dsl.types.CallJava;
import io.quarkiverse.flow.dsl.types.UniqueIdBiFunction;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Verifies that {@code agent(..)} tasks hold an {@link AgentFunction}, which is what makes them recognizable as agent
 * invocations, while {@code withUniqueId(..)} and {@code function(..)} tasks do not. Nothing is written to the
 * workflow document.
 */
class FlowDSLAgentTaskTest {

    private static final UniqueIdBiFunction<String, String> FN = (uniqueId, payload) -> payload;

    @Test
    @DisplayName("test_agent_task_holds_an_agent_function")
    void test_agent_task_holds_an_agent_function() {
        Workflow wf = FlowWorkflowBuilder.workflow("wf-agent-task")
                .tasks(agent("analyst", FN, String.class),
                        withUniqueId("plain", FN, String.class),
                        function("plainFunction", (String input) -> input, String.class),
                        agent(FN, String.class))
                .build();

        assertThat(wf.getDo())
                .extracting(item -> isAgentCall(task(wf, item.getName())))
                .containsExactly(true, false, false, true);
    }

    @Test
    @DisplayName("test_agent_task_writes_no_task_metadata")
    void test_agent_task_writes_no_task_metadata() {
        Workflow wf = FlowWorkflowBuilder.workflow("wf-agent-metadata")
                .tasks(agent("analyst", FN, String.class))
                .build();

        assertThat(task(wf, "analyst").getMetadata())
                .as("the agent marker lives in the function object, not in the workflow document")
                .isNull();
    }

    @Test
    @DisplayName("test_agent_task_still_resolves_to_the_filter_function_executor")
    void test_agent_task_still_resolves_to_the_filter_function_executor() {
        Workflow wf = FlowWorkflowBuilder.workflow("wf-agent-executor")
                .tasks(agent("analyst", FN, String.class))
                .build();

        assertThat(new JavaCallFunctionBuilder().init(callFunction(wf, "analyst"), null, null).get())
                .isInstanceOf(JavaFilterFunctionCallExecutor.class);
    }

    @Test
    @DisplayName("test_agent_function_survives_a_yaml_round_trip")
    void test_agent_function_survives_a_yaml_round_trip() throws IOException {
        Workflow wf = FlowWorkflowBuilder.workflow("wf-agent-round-trip")
                .tasks(agent("analyst", FN, String.class),
                        withUniqueId("plain", FN, String.class))
                .build();

        Workflow roundTripped = TestSerializationUtils.writeAndReadInMemory(wf);

        assertThat(roundTripped.getDo())
                .extracting(item -> isAgentCall(task(roundTripped, item.getName())))
                .containsExactly(true, false);
    }

    private static boolean isAgentCall(TaskBase task) {
        return CallJava.functionOf(task)
                .filter(AgentFunction.class::isInstance)
                .isPresent();
    }

    private static CallFunction callFunction(Workflow wf, String name) {
        return (CallFunction) task(wf, name);
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
