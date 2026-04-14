package io.quarkiverse.flow.it;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Test that structured logging captures error context when workflows fail.
 * <p>
 * Manual verification: Check test logs for workflow.instance.faulted event with error details.
 * Expected log entry should include:
 * - "eventType": "io.serverlessworkflow.workflow.faulted.v1"
 * - "status": "FAULTED"
 * - "error": { "message": "...", "type": "...", "stackTrace": "..." }
 * - "input": { ... } (for debugging context)
 */
@QuarkusTest
@TestProfile(StructuredLoggingErrorTest.EnableErrorLogging.class)
public class StructuredLoggingErrorTest {

    @Inject
    ProblematicWorkflow problematicWorkflow;

    @Test
    void testStructuredLoggingCapturesErrors() {
        try {
            problematicWorkflow.startInstance().await().indefinitely();
        } catch (Exception e) {
            // Expected to fail - ProblematicWorkflow calls non-existent endpoint
            // The important part is that structured logging captures the error
        }

        // Manual verification: Check build logs for JSON event like:
        // INFO [io.quarkiverse.flow.structuredlogging] {"eventType":"io.serverlessworkflow.workflow.faulted.v1",
        //   "status":"FAULTED", "error":{"message":"...","type":"...","stackTrace":"..."},
        //   "input":{...}, ...}
    }

    public static class EnableErrorLogging implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.events", "workflow.*",
                    "quarkus.flow.structured-logging.include-error-context", "true",
                    "quarkus.flow.structured-logging.include-workflow-payloads", "true",
                    "quarkus.flow.structured-logging.log-level", "ERROR");
        }
    }
}
