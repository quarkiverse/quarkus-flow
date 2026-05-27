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
@DisplayName("RuntimeParallelAgenticFlow unit tests")
public class RuntimeParallelAgenticFlowTest {

    @Inject
    RuntimeWorkflowApplicationProvider runtimeAppProvider;

    private RuntimeParallelAgenticFlow flow;

    @BeforeEach
    void setUp() {
        flow = new RuntimeParallelAgenticFlow("io.test.TestParallelAgent", runtimeAppProvider);
    }

    @Test
    @DisplayName("agentClassName_returns_value_from_constructor")
    void agentClassName_returns_value_from_constructor() {
        assertThat(flow.agentClassName()).isEqualTo("io.test.TestParallelAgent");
    }

    @Test
    @DisplayName("subAgentTaskNames_initially_empty")
    void subAgentTaskNames_initially_empty() {
        assertThat(flow.subAgentTaskNames()).isEmpty();
    }

    @Test
    @DisplayName("addSubAgentTaskName_string_adds_task_name")
    void addSubAgentTaskName_string_adds_task_name() {
        flow.addSubAgentTaskName("parallelTask1");
        flow.addSubAgentTaskName("parallelTask2");
        flow.addSubAgentTaskName("parallelTask3");

        assertThat(flow.subAgentTaskNames())
                .containsExactly("parallelTask1", "parallelTask2", "parallelTask3");
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
        var agent3 = AgenticServices.agentAction(scope -> {
        });

        flow.addSubAgentTaskName(agent1, agent2, agent3);

        List<String> taskNames = flow.subAgentTaskNames();
        assertThat(taskNames).hasSize(3);
        assertThat(taskNames).allMatch(name -> name.contains("AgenticScopeAction"));
    }

    @Test
    @DisplayName("addSubAgentTaskName_collection_adds_class_names")
    void addSubAgentTaskName_collection_adds_class_names() {
        var agent1 = AgenticServices.agentAction(scope -> {
        });
        var agent2 = AgenticServices.agentAction(scope -> {
        });

        flow.addSubAgentTaskName(Arrays.asList(agent1, agent2));

        assertThat(flow.subAgentTaskNames()).hasSize(2);
    }

    @Test
    @DisplayName("init_completes_without_error")
    void init_completes_without_error() {
        flow.addSubAgentTaskName("task1");

        // init() should complete without throwing
        flow.init();

        // Verify the workflow descriptor is created (descriptor() returns a Workflow object)
        assertThat(flow.descriptor()).isNotNull();
    }

    @Test
    @DisplayName("multiple_parallel_branches_accumulate")
    void multiple_parallel_branches_accumulate() {
        // Parallel flows typically have multiple branches that run concurrently
        flow.addSubAgentTaskName("branch1");
        flow.addSubAgentTaskName("branch2");
        flow.addSubAgentTaskName("branch3");
        flow.addSubAgentTaskName("branch4");

        assertThat(flow.subAgentTaskNames())
                .hasSize(4)
                .containsExactly("branch1", "branch2", "branch3", "branch4");
    }
}
