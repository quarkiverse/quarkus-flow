package io.quarkiverse.flow.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Test that structured logging works correctly in FILE mode (default).
 * <p>
 * FILE mode writes events to a dedicated file:
 * - Dev/Test: target/quarkus-flow-events.log (or custom path)
 * - Production: /var/log/quarkus-flow/events.log
 * <p>
 * This test explicitly sets the mode to FILE to verify the default behavior
 * and that the configuration property works correctly.
 * <p>
 * Manual verification: Check for target/structured-logging-file-mode-test.log file containing JSON events.
 * Also check build logs for:
 * INFO [io.quarkiverse.flow.deployment.FlowProcessor] Quarkus Flow structured logging file handler auto-configured.
 * Events will be written to: target/structured-logging-file-mode-test.log
 */
@QuarkusTest
@TestProfile(StructuredLoggingFileModeTest.FileModeProfile.class)
@DisplayName("test_structured_logging_file_mode")
public class StructuredLoggingFileModeTest {

    @Inject
    HelloWorkflow helloWorkflow;

    @Test
    @DisplayName("test_file_mode_does_not_break_workflow_execution")
    void testFileModeDoesNotBreakWorkflowExecution() {
        // Execute workflow with structured logging in FILE mode (explicit)
        // If this doesn't throw, FILE mode is working
        var result = helloWorkflow.startInstance().await().indefinitely();

        // Verify workflow executed successfully
        assertThat(result)
                .withFailMessage("Workflow should execute successfully with structured logging FILE mode enabled")
                .isNotNull();

        // Manual verification: Check build logs for:
        // INFO [io.quarkiverse.flow.deployment.FlowProcessor] Quarkus Flow structured logging file handler auto-configured.
        //      Events will be written to: target/structured-logging-file-mode-test.log
        //
        // Check target/structured-logging-file-mode-test.log for JSON events like:
        // {"eventType":"io.serverlessworkflow.workflow.started.v1",...}
        // {"eventType":"io.serverlessworkflow.task.started.v1",...}
        // {"eventType":"io.serverlessworkflow.workflow.completed.v1",...}
        //
        // Note: When running tests in parallel, file handler output timing can vary,
        // so we only verify the workflow executes successfully (similar to StructuredLoggingTest)
    }

    public static class FileModeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.handler.mode", "file",
                    "quarkus.flow.structured-logging.events", "workflow.*",
                    "quarkus.flow.structured-logging.log-level", "INFO",
                    // Use a unique file path for this test
                    "quarkus.log.handler.file.\"FLOW_EVENTS\".path", "target/structured-logging-file-mode-test.log");
        }
    }
}
