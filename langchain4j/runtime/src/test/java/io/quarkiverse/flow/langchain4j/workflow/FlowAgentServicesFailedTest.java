package io.quarkiverse.flow.langchain4j.workflow;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled("Must fix errorHandler on Planner implementation first")
public class FlowAgentServicesFailedTest {

    private static Logger LOG = LoggerFactory.getLogger(FlowAgentServicesFailedTest.class);

    @Inject
    WorkflowRegistry registry;

    @Test
    void checkSessionIsClosedAfterFailure() {
        var a = AgenticServices.agentAction(scope -> {
            String input = scope.readState("input", "");
            if (input.startsWith("boom"))
                throw new RuntimeException("boom in A");
            scope.writeState("calledA", true);
        });
        var b = AgenticServices.agentAction(scope -> scope.writeState("calledB", true));
        var c = AgenticServices.agentAction(scope -> scope.writeState("calledC", true));

        FlowParallelAgentService<TestParallelAgent> service = FlowParallelAgentService.builder(TestParallelAgent.class,
                registry);
        service.subAgents(a, b, c);

        TestParallelAgent agent = service.build();
        try {
            agent.run("boom");
            fail("Should have failed");
        } catch (Exception e) {
            LOG.info("Agent failed as expected: {}", e.getMessage());
        }

        await().atMost(2, SECONDS)
                .untilAsserted(() -> assertThat(FlowPlannerSessions.getInstance().activeSessionCount()).isEqualTo(0));
    }

    interface TestParallelAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }
}
