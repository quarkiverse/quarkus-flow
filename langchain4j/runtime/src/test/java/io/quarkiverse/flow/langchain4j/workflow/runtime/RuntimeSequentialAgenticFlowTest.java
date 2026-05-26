package io.quarkiverse.flow.langchain4j.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@DisplayName("RuntimeSequentialAgenticFlow unit tests")
public class RuntimeSequentialAgenticFlowTest {

    @Inject
    RuntimeWorkflowApplicationProvider runtimeAppProvider;

    private RuntimeSequentialAgenticFlow flow;

    @BeforeEach
    void setUp() {
        flow = new RuntimeSequentialAgenticFlow("io.test.TestAgent", runtimeAppProvider);
    }

    @Test
    @DisplayName("agentClassName_returns_value_from_constructor")
    void agentClassName_returns_value_from_constructor() {
        assertThat(flow.agentClassName()).isEqualTo("io.test.TestAgent");
    }

    @Test
    @DisplayName("subAgentTaskNames_initially_empty")
    void subAgentTaskNames_initially_empty() {
        assertThat(flow.subAgentTaskNames()).isEmpty();
    }

    @Test
    @DisplayName("addSubAgentTaskName_string_adds_task_name")
    void addSubAgentTaskName_string_adds_task_name() {
        flow.addSubAgentTaskName("task1");
        flow.addSubAgentTaskName("task2");

        assertThat(flow.subAgentTaskNames())
                .containsExactly("task1", "task2");
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

        assertThatThrownBy(() -> flow.addSubAgentTaskName("   "))
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
        // Both should be the AgenticScopeAction implementation class name from LangChain4j
        assertThat(taskNames.get(0)).contains("AgenticScopeAction");
        assertThat(taskNames.get(1)).contains("AgenticScopeAction");
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

        List<String> taskNames = flow.subAgentTaskNames();
        assertThat(taskNames).hasSize(3);
        assertThat(taskNames.get(0)).contains("AgenticScopeAction");
        assertThat(taskNames.get(1)).contains("AgenticScopeAction");
        assertThat(taskNames.get(2)).contains("AgenticScopeAction");
    }

    @Test
    @DisplayName("init_completes_without_error")
    void init_completes_without_error() {
        // Add at least one task name so descriptor() creates valid workflow
        flow.addSubAgentTaskName("task1");

        // Initialize - should not throw
        flow.init();

        // Verify the workflow descriptor is created (descriptor() returns a Workflow object)
        assertThat(flow.descriptor()).isNotNull();
    }

    @Test
    @DisplayName("multiple_addSubAgentTaskName_calls_accumulate")
    void multiple_addSubAgentTaskName_calls_accumulate() {
        flow.addSubAgentTaskName("task1");
        flow.addSubAgentTaskName("task2");

        var agent = AgenticServices.agentAction(scope -> {
        });
        flow.addSubAgentTaskName(agent);

        flow.addSubAgentTaskName(Arrays.asList(
                AgenticServices.agentAction(scope -> {
                }),
                AgenticServices.agentAction(scope -> {
                })));

        // Should have: "task1", "task2", agent.class.name, 2 more agent class names = 5 total
        assertThat(flow.subAgentTaskNames()).hasSize(5);
        assertThat(flow.subAgentTaskNames().get(0)).isEqualTo("task1");
        assertThat(flow.subAgentTaskNames().get(1)).isEqualTo("task2");
        assertThat(flow.subAgentTaskNames().get(2)).contains("AgenticScopeAction");
        assertThat(flow.subAgentTaskNames().get(3)).contains("AgenticScopeAction");
        assertThat(flow.subAgentTaskNames().get(4)).contains("AgenticScopeAction");
    }
}
