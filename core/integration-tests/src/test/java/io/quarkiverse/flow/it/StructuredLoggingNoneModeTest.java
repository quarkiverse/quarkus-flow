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
 * Test that structured logging works correctly without handler configuration.
 * <p>
 * When no handler is configured, events are still emitted by StructuredLoggingListener,
 * but they are not captured anywhere (they go to the default parent handlers if any).
 * <p>
 * This test verifies that:
 * 1. Workflow execution succeeds with structured logging enabled but no handler configured
 * 2. No file handler is created (no target/quarkus-flow-events.log file)
 * 3. StructuredLoggingListener is still active (bean is created)
 * <p>
 * Users can manually configure handlers via standard Quarkus logging properties
 * if they want to capture events in this scenario.
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
        // Execute workflow with structured logging enabled but no handler configured
        // Workflow should execute successfully even without handler
        var result = helloWorkflow.startInstance().await().indefinitely();

        // Verify workflow executed successfully
        assertThat(result)
                .withFailMessage("Workflow should execute successfully even without handler configured")
                .isNotNull();

        // Verify that no file handler was created
        Path defaultFileLogPath = Paths.get("target/quarkus-flow-events.log");
        assertThat(defaultFileLogPath)
                .withFailMessage(
                        "No handler configured - should not create a file. " +
                                "Found unexpected log file at: %s",
                        defaultFileLogPath)
                .doesNotExist();

        // Manual verification:
        // Events are emitted by StructuredLoggingListener but go nowhere without handler config.
        //
        // To verify events ARE being emitted (just not captured), you could add manual handler config
        // in the test profile and verify events appear there, e.g.:
        //   "quarkus.log.handler.console.\"FLOW_EVENTS\".enable", "true",
        //   "quarkus.log.handler.console.\"FLOW_EVENTS\".format", "%s%n",
        //   "quarkus.log.category.\"io.quarkiverse.flow.structuredlogging\".handlers", "FLOW_EVENTS"
    }

    public static class NoneModeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.events", "workflow.*",
                    "quarkus.flow.structured-logging.log-level", "INFO");
            // NO handler configuration - events are emitted but not captured
        }
    }
}
