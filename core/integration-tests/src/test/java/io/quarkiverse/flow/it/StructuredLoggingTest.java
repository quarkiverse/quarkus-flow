package io.quarkiverse.flow.it;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Test that structured logging can be enabled and doesn't break workflow execution.
 * <p>
 * Manual verification: Check test logs for JSON events from io.quarkiverse.flow.structuredlogging logger.
 * Expected log entries:
 * - {"eventType":"io.serverlessworkflow.workflow.started.v1", "instanceId":"...", "workflowName":"hello", ...}
 * - {"eventType":"io.serverlessworkflow.task.started.v1", "taskName":"...", ...}
 * - {"eventType":"io.serverlessworkflow.workflow.completed.v1", ...}
 */
@QuarkusTest
@TestProfile(StructuredLoggingTest.EnableStructuredLogging.class)
public class StructuredLoggingTest {

    @Inject
    HelloWorkflow helloWorkflow;

    @Test
    void testStructuredLoggingDoesNotBreakWorkflow() {
        // Execute workflow with structured logging enabled
        // If this doesn't throw, structured logging is working
        helloWorkflow.startInstance().await().indefinitely();

        // Manual verification: Check build logs for JSON events like:
        // INFO [io.quarkiverse.flow.structuredlogging] {"eventType":"io.serverlessworkflow.workflow.started.v1",...}
    }

    public static class EnableStructuredLogging implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.events", "workflow.*",
                    "quarkus.flow.structured-logging.log-level", "INFO");
        }
    }
}
