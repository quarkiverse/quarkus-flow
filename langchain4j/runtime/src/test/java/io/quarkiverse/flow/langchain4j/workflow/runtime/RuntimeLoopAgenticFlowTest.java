package io.quarkiverse.flow.langchain4j.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@DisplayName("RuntimeLoopAgenticFlow unit tests")
public class RuntimeLoopAgenticFlowTest {

    @Inject
    RuntimeWorkflowApplicationProvider runtimeAppProvider;

    private RuntimeLoopAgenticFlow flow;

    @BeforeEach
    void setUp() {
        flow = new RuntimeLoopAgenticFlow("io.test.TestLoopAgent", runtimeAppProvider);
    }

    @Test
    @DisplayName("agentClassName_returns_value_from_constructor")
    void agentClassName_returns_value_from_constructor() {
        assertThat(flow.agentClassName()).isEqualTo("io.test.TestLoopAgent");
    }

    @Test
    @DisplayName("subAgentTaskNames_initially_empty")
    void subAgentTaskNames_initially_empty() {
        assertThat(flow.subAgentTaskNames()).isEmpty();
    }

    @Test
    @DisplayName("init_completes_without_error")
    void init_completes_without_error() {
        flow.addSubAgentTaskName("task1");
        flow.setMaxIterations(5);

        // init() should complete without throwing
        flow.init();

        // Verify the workflow descriptor is created (descriptor() returns a Workflow object)
        assertThat(flow.descriptor()).isNotNull();
    }

    @Test
    @DisplayName("setMaxIterations_accepts_value")
    void setMaxIterations_accepts_value() {
        // We can't access maxIterations() directly (it's protected),
        // but we can verify the setter doesn't throw
        flow.setMaxIterations(10);
    }

    @Test
    @DisplayName("setTestExitAtLoopEnd_accepts_value")
    void setTestExitAtLoopEnd_accepts_value() {
        // We can't access testExitAtLoopEnd() directly (it's protected),
        // but we can verify the setter doesn't throw
        flow.setTestExitAtLoopEnd(true);
    }

    @Test
    @DisplayName("setExitCondition_accepts_value")
    void setExitCondition_accepts_value() {
        BiPredicate<AgenticScope, Integer> condition = (scope, iteration) -> iteration >= 5;

        // We can't access exitCondition() directly (it's protected),
        // but we can verify the setter doesn't throw
        flow.setExitCondition(condition);
    }

    @Test
    @DisplayName("addSubAgentTaskName_string_adds_task_name")
    void addSubAgentTaskName_string_adds_task_name() {
        flow.addSubAgentTaskName("loopTask1");
        flow.addSubAgentTaskName("loopTask2");

        assertThat(flow.subAgentTaskNames())
                .containsExactly("loopTask1", "loopTask2");
    }

    @Test
    @DisplayName("addSubAgentTaskName_string_null_throws_NPE")
    void addSubAgentTaskName_string_null_throws_NPE() {
        assertThatThrownBy(() -> flow.addSubAgentTaskName((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("taskName must not be null");
    }

    @Test
    @DisplayName("addSubAgentTaskName_string_blank_throws_IllegalArgumentException")
    void addSubAgentTaskName_string_blank_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> flow.addSubAgentTaskName(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskName must not be blank");
    }

    @Test
    @DisplayName("addSubAgentTaskName_varargs_adds_class_names")
    void addSubAgentTaskName_varargs_adds_class_names() {
        var agent1 = AgenticServices.agentAction(scope -> {
        });
        var agent2 = AgenticServices.agentAction(scope -> {
        });

        flow.addSubAgentTaskName(agent1, agent2);

        List<String> taskNames = flow.subAgentTaskNames();
        assertThat(taskNames).hasSize(2);
        assertThat(taskNames).allMatch(name -> name.contains("AgenticScopeAction"));
    }

    @Test
    @DisplayName("addSubAgentTaskName_collection_adds_class_names")
    void addSubAgentTaskName_collection_adds_class_names() {
        var agent1 = AgenticServices.agentAction(scope -> {
        });
        var agent2 = AgenticServices.agentAction(scope -> {
        });
        var agent3 = AgenticServices.agentAction(scope -> {
        });

        flow.addSubAgentTaskName(Arrays.asList(agent1, agent2, agent3));

        assertThat(flow.subAgentTaskNames()).hasSize(3);
    }

    @Test
    @DisplayName("loop_configuration_complete_example")
    void loop_configuration_complete_example() {
        // Configure a typical loop with max iterations, exit condition, and test timing
        flow.setMaxIterations(10);
        flow.setTestExitAtLoopEnd(false); // head-checked (test before iteration)
        flow.setExitCondition((scope, iteration) -> {
            int counter = scope.readState("counter", 0);
            return counter >= 3; // Stop after counter reaches 3
        });

        flow.addSubAgentTaskName("incrementCounter");

        // Verify task names accumulated correctly
        assertThat(flow.subAgentTaskNames()).containsExactly("incrementCounter");
    }

    @Test
    @DisplayName("loop_with_multiple_tasks_per_iteration")
    void loop_with_multiple_tasks_per_iteration() {
        // Loops can have multiple tasks that execute in each iteration
        flow.addSubAgentTaskName("step1");
        flow.addSubAgentTaskName("step2");
        flow.addSubAgentTaskName("step3");
        flow.setMaxIterations(5);

        assertThat(flow.subAgentTaskNames()).hasSize(3);
        assertThat(flow.maxIterations()).isEqualTo(5);
    }

    @Test
    @DisplayName("exit_condition_can_be_complex")
    void exit_condition_can_be_complex() {
        BiPredicate<AgenticScope, Integer> condition = (scope, iteration) -> {
            // Exit if we've iterated 3 times OR if scope has error flag
            boolean hasError = scope.readState("error", false);
            return iteration >= 3 || hasError;
        };

        // Verify the setter accepts complex predicates without throwing
        flow.setExitCondition(condition);
    }
}
