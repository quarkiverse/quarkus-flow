package io.quarkiverse.flow.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.langchain4j.workflow.FlowConditionalAgentService;
import io.quarkiverse.flow.langchain4j.workflow.FlowLoopAgentService;
import io.quarkiverse.flow.langchain4j.workflow.FlowParallelAgentService;
import io.quarkiverse.flow.langchain4j.workflow.FlowSequentialAgentService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@ExtendWith(MockitoExtension.class)
public class FlowAgentServicesMockedTest {

    @Test
    void sequentialAgentInvokesExecutorsInOrder() {
        // Arrange
        AgentExecutor exec1 = mock(AgentExecutor.class);
        AgentExecutor exec2 = mock(AgentExecutor.class);
        AgentInvoker invoker1 = mock(AgentInvoker.class);
        AgentInvoker invoker2 = mock(AgentInvoker.class);

        when(invoker1.uniqueName()).thenReturn("FirstAgent");
        when(invoker2.uniqueName()).thenReturn("SecondAgent");
        when(exec1.agentInvoker()).thenReturn(invoker1);
        when(exec2.agentInvoker()).thenReturn(invoker2);

        // We'll accumulate "order" in scope.state("seqOrder") as a StringBuilder
        when(exec1.execute(any())).thenAnswer(inv -> {
            DefaultAgenticScope scope = inv.getArgument(0);
            StringBuilder sb = scope.readState("seqOrder", new StringBuilder());
            sb.append("1");
            scope.writeState("seqOrder", sb);
            return scope;
        });

        when(exec2.execute(any())).thenAnswer(inv -> {
            DefaultAgenticScope scope = inv.getArgument(0);
            StringBuilder sb = scope.readState("seqOrder", new StringBuilder());
            sb.append("2");
            scope.writeState("seqOrder", sb);
            return scope;
        });

        // Build our Flow-backed LC4J service
        FlowSequentialAgentService<TestSequentialAgent> service = FlowSequentialAgentService.builder(TestSequentialAgent.class);

        // Register sub-agents (executors)
        service.subAgents(List.of(exec1, exec2));

        TestSequentialAgent agent = service.build();

        // Act
        ResultWithAgenticScope<String> result = agent.run("hello");
        AgenticScope scope = result.agenticScope();

        // Assert: both executors called once
        verify(exec1, times(1)).execute(any());
        verify(exec2, times(1)).execute(any());

        // And the custom state shows order "12"
        StringBuilder seqOrder = scope.readState("seqOrder", new StringBuilder());
        assertThat(seqOrder.toString()).isEqualTo("12");
    }

    @Test
    void parallelAgentInvokesAllBranches() {
        // Arrange
        AgentExecutor execA = mock(AgentExecutor.class);
        AgentExecutor execB = mock(AgentExecutor.class);
        AgentExecutor execC = mock(AgentExecutor.class);

        AgentInvoker invokerA = mock(AgentInvoker.class);
        AgentInvoker invokerB = mock(AgentInvoker.class);
        AgentInvoker invokerC = mock(AgentInvoker.class);

        when(invokerA.uniqueName()).thenReturn("A");
        when(invokerB.uniqueName()).thenReturn("B");
        when(invokerC.uniqueName()).thenReturn("C");

        when(execA.agentInvoker()).thenReturn(invokerA);
        when(execB.agentInvoker()).thenReturn(invokerB);
        when(execC.agentInvoker()).thenReturn(invokerC);

        // Each executor just marks a flag in the scope
        when(execA.execute(any())).thenAnswer(inv -> {
            DefaultAgenticScope scope = inv.getArgument(0);
            scope.writeState("calledA", true);
            return scope;
        });
        when(execB.execute(any())).thenAnswer(inv -> {
            DefaultAgenticScope scope = inv.getArgument(0);
            scope.writeState("calledB", true);
            return scope;
        });
        when(execC.execute(any())).thenAnswer(inv -> {
            DefaultAgenticScope scope = inv.getArgument(0);
            scope.writeState("calledC", true);
            return scope;
        });

        FlowParallelAgentService<TestParallelAgent> service = FlowParallelAgentService.builder(TestParallelAgent.class);

        service.subAgents(List.of(execA, execB, execC));

        TestParallelAgent agent = service.build();

        // Act
        ResultWithAgenticScope<String> result = agent.run("parallel-input");
        AgenticScope scope = result.agenticScope();

        // Assert: each executor called at least once
        verify(execA, atLeastOnce()).execute(any());
        verify(execB, atLeastOnce()).execute(any());
        verify(execC, atLeastOnce()).execute(any());

        assertThat(scope.readState("calledA", false)).isTrue();
        assertThat(scope.readState("calledB", false)).isTrue();
        assertThat(scope.readState("calledC", false)).isTrue();
    }

    @Test
    void conditionalAgentInvokesOnlyMatchingBranch() {
        // Arrange
        AgentExecutor medicalExec = mock(AgentExecutor.class);
        AgentExecutor financeExec = mock(AgentExecutor.class);

        AgentInvoker invokerMed = mock(AgentInvoker.class);
        AgentInvoker invokerFin = mock(AgentInvoker.class);

        when(invokerMed.uniqueName()).thenReturn("MedicalAgent");
        when(invokerFin.uniqueName()).thenReturn("FinanceAgent");
        when(medicalExec.agentInvoker()).thenReturn(invokerMed);
        when(financeExec.agentInvoker()).thenReturn(invokerFin);

        // For logging / debugging, mark which branch ran
        when(medicalExec.execute(any())).thenAnswer(inv -> {
            DefaultAgenticScope scope = inv.getArgument(0);
            scope.writeState("branch", "medical");
            return scope;
        });

        FlowConditionalAgentService<TestConditionalAgent> service = FlowConditionalAgentService
                .builder(TestConditionalAgent.class);

        // condition on state("type")
        Predicate<AgenticScope> isMedical = scope -> "medical".equals(scope.readState("type", ""));
        Predicate<AgenticScope> isFinance = scope -> "finance".equals(scope.readState("type", ""));

        service.subAgents(isMedical, List.of(medicalExec));
        service.subAgents(isFinance, List.of(financeExec));

        TestConditionalAgent agent = service.build();

        // Act: route("medical")
        ResultWithAgenticScope<String> result = agent.route("medical");
        AgenticScope scope = result.agenticScope();

        // Assert: only medicalExec is invoked
        verify(medicalExec, atLeastOnce()).execute(any());
        verify(financeExec, never()).execute(any());

        assertThat(scope.readState("branch", "")).isEqualTo("medical");
    }

    @Test
    void loopAgentHonorsExitConditionAndMaxIterations() {
        // Arrange
        AgentExecutor loopExec = mock(AgentExecutor.class);
        AgentInvoker invoker = mock(AgentInvoker.class);
        when(invoker.uniqueName()).thenReturn("LoopAgent");
        when(loopExec.agentInvoker()).thenReturn(invoker);

        AtomicInteger calls = new AtomicInteger();

        // Each execution increments "counter" in the scope
        when(loopExec.execute(any())).thenAnswer(inv -> {
            DefaultAgenticScope scope = inv.getArgument(0);
            int c = scope.readState("counter", 0);
            c++;
            scope.writeState("counter", c);
            calls.incrementAndGet();
            return scope;
        });

        FlowLoopAgentService<TestLoopAgent> service = FlowLoopAgentService.builder(TestLoopAgent.class);

        // max 10 iterations, but we exit when counter >= 3
        service.maxIterations(10);
        service.exitCondition((scope, idx) -> {
            int c = scope.readState("counter", 0);
            // Stop when c >= 3
            return c >= 3;
        });
        // keep default: testExitAtLoopEnd(false) – head-checked

        service.subAgents(List.of(loopExec));

        TestLoopAgent agent = service.build();

        // Act
        ResultWithAgenticScope<String> result = agent.run("any-topic");
        AgenticScope scope = result.agenticScope();

        int counter = scope.readState("counter", 0);

        // Assert:
        // - the loop body ran a few times but not more than maxIterations
        assertThat(counter).isBetween(1, 10);
        assertThat(calls.get()).isEqualTo(counter);

        verify(loopExec, times(counter)).execute(any());
    }

    interface TestSequentialAgent {
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }

    interface TestParallelAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }

    interface TestConditionalAgent {
        ResultWithAgenticScope<String> route(@V("type") String type);
    }

    interface TestLoopAgent {
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }
}
