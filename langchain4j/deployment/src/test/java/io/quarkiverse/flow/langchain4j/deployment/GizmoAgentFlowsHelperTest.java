package io.quarkiverse.flow.langchain4j.deployment;

import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.computeTaskNames;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateClassName;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateConditionalMetadataField;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateLoopMetadataFields;
import static io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper.generateSubAgentTaskNamesMethod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.langchain4j.workflow.flow.AgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.flow.ConditionalAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.flow.LoopAgenticFlow;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;

/**
 * Unit tests for Gizmo bytecode generation helpers.
 * <p>
 * These tests verify that the generation methods execute without errors.
 * Full integration testing is done in FlowLangChain4jProcessorTest.
 */
class GizmoAgentFlowsHelperTest {

    private static Index buildIndex(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            indexer.indexClass(clazz);
        }
        return indexer.complete();
    }

    @Test
    @DisplayName("generateClassName should convert agent interface FQCN to generated class name")
    void test_generateClassName() {
        // With package
        assertThat(generateClassName("com.example.agents.CustomerAgent"))
                .isEqualTo("com.example.agents.GeneratedCustomerAgentFlow");

        // Without package (default package)
        assertThat(generateClassName("SimpleAgent")).isEqualTo("GeneratedSimpleAgentFlow");

        // Nested package
        assertThat(generateClassName("io.quarkiverse.flow.test.agents.OrderAgent"))
                .isEqualTo("io.quarkiverse.flow.test.agents.GeneratedOrderAgentFlow");
    }

    @Test
    @DisplayName("generateConditionalMetadataField should not throw exceptions")
    void test_generateConditionalMetadataField_doesNotThrow() throws IOException {
        Index index = buildIndex(TestAgent1.class);
        String agentClassName = TestAgent1.class.getName();
        List<Type> subAgents = List.of(Type.create(DotName.createSimple(agentClassName), Type.Kind.CLASS));

        ConditionalMetadata metadata = new ConditionalMetadata(Map.of(agentClassName, new PredicateMetadata(
                "shouldActivateAgent1", List.of(AgenticScope.class.getName()), "Activation predicate for Agent1")));

        assertThatCode(() -> {
            ClassOutput output = (name, data) -> {
                // no-op - just verify bytecode generation doesn't throw
            };

            try (ClassCreator creator = ClassCreator.builder().classOutput(output).className("test.GeneratedConditionalFlow")
                    .superClass(Flow.class).interfaces(ConditionalAgenticFlow.class).build()) {

                generateConditionalMetadataField(creator, TestConditionalAgent.class.getName(), metadata, subAgents);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("generateLoopMetadataFields should not throw exceptions")
    void test_generateLoopMetadataFields_doesNotThrow() {
        LoopMetadata metadata = new LoopMetadata(10, java.util.Optional.of(new PredicateMetadata("shouldExit",
                List.of(AgenticScope.class.getName(), Integer.class.getName()), "Exit condition for loop")), true);

        assertThatCode(() -> {
            ClassOutput output = (name, data) -> {
                // no-op - just verify bytecode generation doesn't throw
            };

            try (ClassCreator creator = ClassCreator.builder().classOutput(output).className("test.GeneratedLoopFlow")
                    .superClass(Flow.class).interfaces(LoopAgenticFlow.class).build()) {

                generateLoopMetadataFields(creator, TestLoopAgent.class.getName(), metadata);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("generateSubAgentTaskNamesMethod should not throw exceptions")
    void test_generateSubAgentTaskNamesMethod_doesNotThrow() {
        List<String> taskNames = List.of("generateStory", "editStory", "reviewStory");

        assertThatCode(() -> {
            ClassOutput output = (name, data) -> {
                // no-op - just verify bytecode generation doesn't throw
            };

            try (ClassCreator creator = ClassCreator.builder().classOutput(output).className("test.GeneratedTestFlow")
                    .superClass(Flow.class).interfaces(AgenticFlow.class).build()) {

                generateSubAgentTaskNamesMethod(creator, taskNames);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("computeTaskNames should extract method names without indices")
    void test_computeTaskNames_correctFormat() throws IOException {
        Index index = buildIndex(TestAgent1.class, TestAgent2.class);
        List<Type> subAgents = List.of(Type.create(DotName.createSimple(TestAgent1.class.getName()), Type.Kind.CLASS),
                Type.create(DotName.createSimple(TestAgent2.class.getName()), Type.Kind.CLASS));

        List<String> taskNames = computeTaskNames(index, subAgents);

        assertThat(taskNames).hasSize(2).containsExactly("execute", "execute");
    }

    @Test
    @DisplayName("computeTaskNames should return empty list for empty subAgents")
    void test_computeTaskNames_emptySubAgents() throws IOException {
        Index index = buildIndex();

        List<String> taskNames = computeTaskNames(index, List.of());

        assertThat(taskNames).isEmpty();
    }

    @Test
    @DisplayName("computeTaskNames should throw when class not in index")
    void test_computeTaskNames_classNotInIndex() throws IOException {
        Index emptyIndex = buildIndex();
        List<Type> subAgents = List.of(Type.create(DotName.createSimple("com.example.MissingAgent"), Type.Kind.CLASS));

        assertThatThrownBy(() -> computeTaskNames(emptyIndex, subAgents)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Class not found in Jandex index").hasMessageContaining("com.example.MissingAgent");
    }

    @Test
    @DisplayName("computeTaskNames should throw when no agent method found")
    void test_computeTaskNames_noAgentMethod() throws IOException {
        Index index = buildIndex(ClassWithoutAgentAnnotation.class);
        List<Type> subAgents = List
                .of(Type.create(DotName.createSimple(ClassWithoutAgentAnnotation.class.getName()), Type.Kind.CLASS));

        assertThatThrownBy(() -> computeTaskNames(index, subAgents)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No agent method found").hasMessageContaining("ClassWithoutAgentAnnotation")
                .hasMessageContaining("@Agent, @SequenceAgent, @ParallelAgent, @LoopAgent, or @ConditionalAgent");
    }

    @Test
    @DisplayName("computeTaskNames should find methods with @SequenceAgent annotation")
    void test_computeTaskNames_sequenceAgent() throws IOException {
        Index index = buildIndex(TestSequenceAgent.class);
        List<Type> subAgents = List.of(Type.create(DotName.createSimple(TestSequenceAgent.class.getName()), Type.Kind.CLASS));

        List<String> taskNames = computeTaskNames(index, subAgents);

        assertThat(taskNames).hasSize(1).containsExactly("orchestrate");
    }

    @Test
    @DisplayName("computeTaskNames should find methods with @ParallelAgent annotation")
    void test_computeTaskNames_parallelAgent() throws IOException {
        Index index = buildIndex(TestParallelAgent.class);
        List<Type> subAgents = List.of(Type.create(DotName.createSimple(TestParallelAgent.class.getName()), Type.Kind.CLASS));

        List<String> taskNames = computeTaskNames(index, subAgents);

        assertThat(taskNames).hasSize(1).containsExactly("parallel");
    }

    // ========== Helper Methods ==========

    @Test
    @DisplayName("generateClassName should handle nested classes with $ separator")
    void test_generateClassName_nestedClass() {
        assertThat(generateClassName("com.example.Outer$Inner")).isEqualTo("com.example.GeneratedOuter$InnerFlow");

        assertThat(generateClassName("com.example.Outer$Middle$Inner"))
                .isEqualTo("com.example.GeneratedOuter$Middle$InnerFlow");
    }

    // ========== Test Agent Classes ==========

    /**
     * Test agent interface with a static predicate method for conditional activation
     */
    public interface TestConditionalAgent {
        static boolean shouldActivateAgent1(AgenticScope scope) {
            return scope.readState("activate", false);
        }
    }

    /**
     * Test agent interface with static exit predicate for loop workflows
     */
    public interface TestLoopAgent {
        static boolean shouldExit(AgenticScope scope, Integer loopCounter) {
            return scope.readState("shouldExit", false);
        }
    }

    /**
     * Test agent with @Agent annotation
     */
    public interface TestAgent1 {
        @Agent
        String execute();
    }

    /**
     * Another test agent with @Agent annotation
     */
    public interface TestAgent2 {
        @Agent
        String execute();
    }

    /**
     * Class without any agent annotation
     */
    public interface ClassWithoutAgentAnnotation {
        String doSomething();
    }

    /**
     * Test agent with @SequenceAgent annotation
     */
    public interface TestSequenceAgent {
        @SequenceAgent(subAgents = { TestAgent1.class, TestAgent2.class })
        String orchestrate();
    }

    /**
     * Test agent with @ParallelAgent annotation
     */
    public interface TestParallelAgent {
        @ParallelAgent(subAgents = { TestAgent1.class, TestAgent2.class })
        String parallel();
    }
}
