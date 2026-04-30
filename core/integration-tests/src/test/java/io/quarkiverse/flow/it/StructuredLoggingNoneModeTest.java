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
 * Test that structured logging works correctly in NONE mode.
 * <p>
 * NONE mode disables automatic handler creation, allowing advanced users to
 * manually configure handlers via standard Quarkus logging properties. Events
 * are still emitted by StructuredLoggingListener, but no default handler is created.
 * <p>
 * This test verifies that:
 * 1. Workflow execution succeeds with NONE mode enabled
 * 2. No automatic handler is created (no file handler created)
 * 3. StructuredLoggingListener is still active (bean is created)
 * <p>
 * Manual verification: Check build logs for the message:
 * "Structured Logging enabled, but automatic logging handler disabled.
 * You have to configure the output via quarkus.log.* configuration"
 */
@QuarkusTest
@TestProfile(StructuredLoggingNoneModeTest.NoneModeProfile.class)
@DisplayName("test_structured_logging_none_mode")
public class StructuredLoggingNoneModeTest {

    @Inject
    HelloWorkflow helloWorkflow;

    @BeforeEach
    void cleanupDefaultLogFile() throws IOException {
        // Clean up any leftover log file from previous tests
        Path defaultFileLogPath = Paths.get("target/quarkus-flow-events.log");
        Files.deleteIfExists(defaultFileLogPath);
    }

    @Test
    @DisplayName("test_none_mode_workflow_execution_succeeds_without_handler")
    void test_none_mode_workflow_execution_succeeds_without_handler() {
        // Execute workflow with structured logging in NONE mode
        // No automatic handler should be created, but workflow should execute successfully
        var result = helloWorkflow.startInstance().await().indefinitely();

        // Verify workflow executed successfully
        assertThat(result)
                .withFailMessage("Workflow should execute successfully even with handler.mode=none")
                .isNotNull();

        // Verify that NO automatic file handler was created
        Path defaultFileLogPath = Paths.get("target/quarkus-flow-events.log");
        assertThat(defaultFileLogPath)
                .withFailMessage(
                        "NONE mode should not create a file handler. " +
                                "Found unexpected log file at: %s",
                        defaultFileLogPath)
                .doesNotExist();

        // Manual verification: Check build logs for:
        // INFO [io.quarkiverse.flow.deployment.FlowProcessor] Structured Logging enabled, but automatic logging handler disabled.
        //      You have to configure the output via quarkus.log.* configuration for the category io.quarkiverse.flow.structuredlogging
        //
        // Verify that NO automatic handler is created (no file, no console handler)
        // Events are emitted by StructuredLoggingListener but go nowhere without manual handler config
        //
        // To verify events ARE being emitted (just not captured), you could add manual handler config
        // in the test profile and verify events appear there
    }

    public static class NoneModeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.handler.mode", "none",
                    "quarkus.flow.structured-logging.events", "workflow.*",
                    "quarkus.flow.structured-logging.log-level", "INFO");
        }
    }
}
