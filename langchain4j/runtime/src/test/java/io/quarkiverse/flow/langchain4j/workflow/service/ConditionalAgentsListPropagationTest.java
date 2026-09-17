package io.quarkiverse.flow.langchain4j.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import jakarta.enterprise.inject.Vetoed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgent;
import io.quarkiverse.flow.langchain4j.workflow.flow.ConditionalAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeConditionalAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeFlowConditionalAgentService;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeWorkflowApplicationProvider;

/**
 * Unit tests verifying that the conditionalAgents list is properly propagated
 * from ConditionalAgentServiceImpl to the flow for dev-ui topology rendering.
 * <p>
 * This addresses issue #993 - ensuring conditional agents metadata is available.
 */
@DisplayName("ConditionalAgents List Propagation")
class ConditionalAgentsListPropagationTest {

    // Test implementation of ConditionalAgenticFlow - @Vetoed prevents CDI from treating it as a bean
    @Vetoed
    static class TestConditionalFlow extends ConditionalAgenticFlow {
        @Override
        public String agentClassName() {
            return "TestFlow";
        }

        @Override
        protected List<String> subAgentTaskNames() {
            return List.of();
        }
    }

    @Test
    @DisplayName("conditionalAgents list should be copied to RuntimeConditionalAgenticFlow when building")
    void test_runtimeFlow_conditionalAgents_propagated() {
        RuntimeWorkflowApplicationProvider provider = mock(RuntimeWorkflowApplicationProvider.class);

        RuntimeFlowConditionalAgentService<UntypedAgent> service = RuntimeFlowConditionalAgentService.builder(provider);

        // Add subagents - creates ONE ConditionalAgent with 2 subagents inside it
        // (not 2 separate ConditionalAgent entries)
        UntypedAgent mockAgent1 = mock(UntypedAgent.class);
        UntypedAgent mockAgent2 = mock(UntypedAgent.class);
        service.subAgents(scope -> true, mockAgent1, mockAgent2);

        // Access the flow before build to verify it gets populated
        RuntimeConditionalAgenticFlow flow = (RuntimeConditionalAgenticFlow) service.flow;

        // Before build, the list should be empty
        assertThat(flow.conditionalAgents()).isEmpty();

        // Build the service - this copies conditionalAgents to the flow BEFORE init()
        // so the list is populated even if init() throws (which it will due to missing context)
        try {
            service.build();
        } catch (Exception e) {
            // Expected - init() will throw NullPointerException due to missing workflow context,
            // but addConditionalAgents() was already called before init(), so the list is populated
            assertThat(e).isNotNull();
        }

        // After build attempt, the list should be populated
        // One ConditionalAgent entry containing 2 AgentInstances
        List<ConditionalAgent> conditionalAgents = flow.conditionalAgents();
        assertThat(conditionalAgents)
                .isNotNull()
                .hasSize(1)
                .first()
                .satisfies(ca -> {
                    assertThat(ca.agentInstances()).hasSize(2);
                    assertThat(ca.condition()).isNotNull();
                });
    }

    @Test
    @DisplayName("setConditionalAgents should make list immutable")
    void test_setConditionalAgents_makesListImmutable() {
        TestConditionalFlow flow = new TestConditionalFlow();

        ConditionalAgent mockAgent = mock(ConditionalAgent.class);
        flow.setConditionalAgents(List.of(mockAgent));

        List<ConditionalAgent> agents = flow.conditionalAgents();
        assertThat(agents).hasSize(1);

        // Verify the list is immutable by attempting to modify it
        ConditionalAgent anotherMock = mock(ConditionalAgent.class);
        assertThatThrownBy(() -> agents.add(anotherMock))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("default conditionalAgents should return empty list")
    void test_defaultConditionalAgents_isEmpty() {
        TestConditionalFlow flow = new TestConditionalFlow();

        assertThat(flow.conditionalAgents()).isEmpty();
    }
}
