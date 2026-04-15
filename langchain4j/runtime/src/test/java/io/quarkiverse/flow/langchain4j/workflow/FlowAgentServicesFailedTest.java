package io.quarkiverse.flow.langchain4j.workflow;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.util.HashSet;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
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

        // Capture active sessions before running to identify which one is ours
        var sessionsBefore = FlowPlannerSessions.getInstance().activeSessionIds();

        try {
            agent.run("boom");
            fail("Should have failed");
        } catch (Exception e) {
            LOG.info("Agent failed as expected: {}", e.getMessage());
        }

        // Find the new session that was created (might be already cleaned up)
        var sessionsAfter = FlowPlannerSessions.getInstance().activeSessionIds();
        var newSessions = new HashSet<>(sessionsAfter);
        newSessions.removeAll(sessionsBefore);

        // If session still exists, explicitly wait for its cleanup
        for (String sessionId : newSessions) {
            try {
                FlowPlanner planner = FlowPlannerSessions.getInstance().get(sessionId);
                planner.awaitCleanup();
            } catch (IllegalArgumentException e) {
                // Session already cleaned up, which is fine
            }
        }

        // Verify our session(s) are cleaned up - don't check absolute count since other tests may be running in parallel
        await().atMost(2, SECONDS)
                .untilAsserted(() -> {
                    var currentSessions = FlowPlannerSessions.getInstance().activeSessionIds();
                    for (String sessionId : newSessions) {
                        assertThat(currentSessions).as("Session %s should be cleaned up", sessionId)
                                .doesNotContain(sessionId);
                    }
                });
    }

    interface TestParallelAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }
}
