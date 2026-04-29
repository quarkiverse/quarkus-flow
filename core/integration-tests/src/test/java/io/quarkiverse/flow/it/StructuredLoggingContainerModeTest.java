package io.quarkiverse.flow.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Test that structured logging works correctly in CONTAINER mode.
 * <p>
 * CONTAINER mode writes events to stdout instead of a file, which is appropriate
 * for containerized deployments (Kubernetes, Docker) where logs are captured
 * from the container's stdout by the runtime.
 * <p>
 * This test verifies that:
 * 1. Workflow execution succeeds with CONTAINER mode enabled
 * 2. No file handler is created (no target/quarkus-flow-events.log file)
 * 3. Events are written to console handler (manual verification via logs)
 * <p>
 * Manual verification: Check test logs for JSON events from io.quarkiverse.flow.structuredlogging logger.
 * Events should appear in console output.
 * Expected log entries:
 * - {"eventType":"io.serverlessworkflow.workflow.started.v1", "instanceId":"...", "workflowName":"hello", ...}
 * - {"eventType":"io.serverlessworkflow.task.started.v1", "taskName":"...", ...}
 * - {"eventType":"io.serverlessworkflow.workflow.completed.v1", ...}
 */
@QuarkusTest
@TestProfile(StructuredLoggingContainerModeTest.ContainerModeProfile.class)
@DisplayName("test_structured_logging_container_mode")
public class StructuredLoggingContainerModeTest {

    @Inject
    HelloWorkflow helloWorkflow;

    @BeforeEach
    void cleanupDefaultLogFile() throws IOException {
        // Clean up any leftover log file from previous tests
        Path defaultFileLogPath = Paths.get("target/quarkus-flow-events.log");
        Files.deleteIfExists(defaultFileLogPath);
    }

    @Test
    @DisplayName("test_container_mode_writes_to_stdout_not_file")
    void testContainerModeWritesToStdoutNotFile() {
        // Execute workflow with structured logging in CONTAINER mode
        // Events should be written to console (stdout) instead of a file
        var result = helloWorkflow.startInstance().await().indefinitely();

        // Verify workflow executed successfully
        assertThat(result).isNotNull();

        // Verify that the default file handler was NOT created
        // (container mode uses console handler, not file handler)
        Path defaultFileLogPath = Paths.get("target/quarkus-flow-events.log");
        assertThat(defaultFileLogPath)
                .withFailMessage(
                        "CONTAINER mode should not create a file handler. " +
                                "Found unexpected log file at: %s",
                        defaultFileLogPath)
                .doesNotExist();

        // Manual verification: Check build logs for JSON events like:
        // INFO [io.quarkiverse.flow.structuredlogging] {"eventType":"io.serverlessworkflow.workflow.started.v1",...}
        //
        // Events should appear in console output via console handler, NOT in a file
    }

    public static class ContainerModeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.handler.mode", "container",
                    "quarkus.flow.structured-logging.events", "workflow.*",
                    "quarkus.flow.structured-logging.log-level", "INFO");
        }
    }
}
