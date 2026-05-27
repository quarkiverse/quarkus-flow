package io.quarkiverse.flow.langchain4j.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@DisplayName("RuntimeConditionalAgenticFlow unit tests")
public class RuntimeConditionalAgenticFlowTest {

    @Inject
    RuntimeWorkflowApplicationProvider runtimeAppProvider;

    private RuntimeConditionalAgenticFlow flow;

    @BeforeEach
    void setUp() {
        flow = new RuntimeConditionalAgenticFlow("io.test.TestConditionalAgent", runtimeAppProvider);
    }

    @Test
    @DisplayName("agentClassName_returns_value_from_constructor")
    void agentClassName_returns_value_from_constructor() {
        assertThat(flow.agentClassName()).isEqualTo("io.test.TestConditionalAgent");
    }

    @Test
    @DisplayName("subAgentTaskNames_initially_empty")
    void subAgentTaskNames_initially_empty() {
        assertThat(flow.subAgentTaskNames()).isEmpty();
    }

    @Test
    @DisplayName("activationPredicates_initially_empty")
    void activationPredicates_initially_empty() {
        assertThat(flow.activationPredicates()).isEmpty();
    }

    @Test
    @DisplayName("addSubAgentWithPredicate_adds_task_name_and_predicate")
    void addSubAgentWithPredicate_adds_task_name_and_predicate() {
        var agent1 = AgenticServices.agentAction(scope -> {
        });
        var agent2 = AgenticServices.agentAction(scope -> {
        });
        Predicate<AgenticScope> condition1 = scope -> true;
        Predicate<AgenticScope> condition2 = scope -> false;

        flow.addSubAgentWithPredicate(condition1, agent1);
        flow.addSubAgentWithPredicate(condition2, agent2);

        assertThat(flow.subAgentTaskNames()).hasSize(2);
        assertThat(flow.activationPredicates()).hasSize(2);
        assertThat(flow.activationPredicates().get(0)).isSameAs(condition1);
        assertThat(flow.activationPredicates().get(1)).isSameAs(condition2);
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

        flow.addSubAgentTaskName(Arrays.asList(agent1, agent2));

        assertThat(flow.subAgentTaskNames()).hasSize(2);
    }

    @Test
    @DisplayName("init_completes_without_error")
    void init_completes_without_error() {
        var agent = AgenticServices.agentAction(scope -> {
        });
        flow.addSubAgentWithPredicate(scope -> true, agent);

        // init() should complete without throwing
        flow.init();

        // Verify the workflow descriptor is created (descriptor() returns a Workflow object)
        assertThat(flow.descriptor()).isNotNull();
    }

    @Test
    @DisplayName("multiple_conditions_with_predicates_accumulate")
    void multiple_conditions_with_predicates_accumulate() {
        // Conditional flows have multiple branches with different conditions
        var medicalAgent = AgenticServices.agentAction(scope -> scope.writeState("branch", "medical"));
        var financeAgent = AgenticServices.agentAction(scope -> scope.writeState("branch", "finance"));
        var defaultAgent = AgenticServices.agentAction(scope -> scope.writeState("branch", "default"));

        flow.addSubAgentWithPredicate(scope -> "medical".equals(scope.readState("type", "")), medicalAgent);
        flow.addSubAgentWithPredicate(scope -> "finance".equals(scope.readState("type", "")), financeAgent);
        flow.addSubAgentWithPredicate(scope -> true, defaultAgent);

        assertThat(flow.subAgentTaskNames()).hasSize(3);
        assertThat(flow.activationPredicates()).hasSize(3);
    }

    @Test
    @DisplayName("taskNames_and_predicates_stay_in_sync")
    void taskNames_and_predicates_stay_in_sync() {
        var agent1 = AgenticServices.agentAction(scope -> {
        });
        var agent2 = AgenticServices.agentAction(scope -> {
        });
        var agent3 = AgenticServices.agentAction(scope -> {
        });

        flow.addSubAgentWithPredicate(scope -> true, agent1);
        flow.addSubAgentWithPredicate(scope -> false, agent2);
        flow.addSubAgentWithPredicate(scope -> true, agent3);

        // Should have 3 task names and 3 predicates
        assertThat(flow.subAgentTaskNames()).hasSize(3);
        assertThat(flow.activationPredicates()).hasSize(3);
    }
}
