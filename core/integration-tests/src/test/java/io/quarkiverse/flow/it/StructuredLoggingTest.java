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
 * - {"eventType":"workflow.instance.started", "instanceId":"...", "workflowName":"hello", ...}
 * - {"eventType":"workflow.task.started", "taskName":"...", ...}
 * - {"eventType":"workflow.instance.completed", ...}
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
        // INFO [io.quarkiverse.flow.structuredlogging] {"eventType":"workflow.instance.started",...}
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
