package io.quarkiverse.flow.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Test that structured logging produces pure JSON when quarkus-logging-json is present.
 * <p>
 * This validates that our auto-configuration correctly disables JSON formatting
 * for the FLOW_EVENTS file handler to avoid double JSON serialization.
 * <p>
 * <b>IMPORTANT:</b> This test requires the {@code test-logging-json} Maven profile to be active.
 * Run with: {@code mvn test -Ptest-logging-json -Dtest=StructuredLoggingWithQuarkusLoggingJsonTest}
 * <p>
 * The test is automatically skipped if quarkus-logging-json is not on the classpath.
 * <p>
 * Expected: File contains pure workflow event JSON (one event per line)
 * NOT: JSON wrapped in quarkus-logging-json structure
 */
@QuarkusTest
@TestProfile(StructuredLoggingWithQuarkusLoggingJsonTest.WithQuarkusLoggingJson.class)
@EnabledIf("isQuarkusLoggingJsonPresent")
public class StructuredLoggingWithQuarkusLoggingJsonTest {

    @Inject
    HelloWorkflow helloWorkflow;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Condition method for @EnabledIf annotation.
     * Test only runs when quarkus-logging-json is on the classpath.
     */
    static boolean isQuarkusLoggingJsonPresent() {
        try {
            Class.forName("io.quarkus.logging.json.runtime.LoggingJsonRecorder");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    @DisplayName("Structured logging file should contain pure JSON, not double-wrapped JSON")
    void testStructuredLoggingFileContainsPureJson() throws IOException {
        // Execute workflow to generate structured log events
        helloWorkflow.startInstance().await().indefinitely();

        // Read the structured logging file
        Path logFile = Paths.get("target/structured-logging-json-test.log");
        assertThat(logFile).exists();

        List<String> logLines = Files.readAllLines(logFile);
        assertThat(logLines).isNotEmpty();

        // Each line should be a pure workflow event JSON, NOT wrapped by quarkus-logging-json
        for (String line : logLines) {
            // Parse as JSON
            JsonNode event = objectMapper.readTree(line);

            // Should have workflow event fields at root level
            assertThat(event.has("eventType"))
                    .withFailMessage(
                            "Event should have 'eventType' at root level. Found: %s. " +
                                    "This indicates double JSON wrapping (quarkus-logging-json wrapper present).",
                            line)
                    .isTrue();

            assertThat(event.has("instanceId"))
                    .withFailMessage(
                            "Event should have 'instanceId' at root level. Found: %s",
                            line)
                    .isTrue();

            // Should be a Serverless Workflow CloudEvent type
            String eventType = event.get("eventType").asText();
            assertThat(eventType)
                    .withFailMessage(
                            "Event type should be a Serverless Workflow CloudEvent type (io.serverlessworkflow.*). Found: %s",
                            eventType)
                    .startsWith("io.serverlessworkflow.");

            // Should NOT have quarkus-logging-json wrapper fields
            assertThat(event.has("timestamp"))
                    .withFailMessage(
                            "Event should have workflow timestamp, not quarkus-logging-json timestamp wrapper. " +
                                    "If this has 'loggerName', 'level', etc., it's double-wrapped. Found: %s",
                            line)
                    .isTrue();

            // Verify it's NOT the quarkus-logging-json format by checking it doesn't have their fields
            assertThat(event.has("loggerName"))
                    .withFailMessage(
                            "Event should NOT have 'loggerName' field (this is from quarkus-logging-json wrapper). " +
                                    "Found: %s. This indicates double JSON serialization!",
                            line)
                    .isFalse();

            assertThat(event.has("loggerClassName"))
                    .withFailMessage(
                            "Event should NOT have 'loggerClassName' field (this is from quarkus-logging-json wrapper). " +
                                    "Found: %s",
                            line)
                    .isFalse();

            assertThat(event.has("message"))
                    .withFailMessage(
                            "Event should NOT have 'message' field (this would contain the JSON string in double-wrapped format). "
                                    +
                                    "Found: %s",
                            line)
                    .isFalse();
        }

        // Verify we got the expected workflow events
        List<String> eventTypes = logLines.stream()
                .map(line -> {
                    try {
                        return objectMapper.readTree(line).get("eventType").asText();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        assertThat(eventTypes)
                .contains("io.serverlessworkflow.workflow.started.v1")
                .contains("io.serverlessworkflow.workflow.completed.v1");
    }

    public static class WithQuarkusLoggingJson implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // Enable quarkus-logging-json for console (simulating production setup)
                    "quarkus.log.console.json", "true",

                    // Enable structured logging
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.events", "workflow.*",

                    // Use a unique file path for this test
                    "quarkus.log.handler.file.\"FLOW_EVENTS\".path", "target/structured-logging-json-test.log");
        }
    }
}
