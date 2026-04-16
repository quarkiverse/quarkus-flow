package io.quarkiverse.flow.it;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Test that structured logging truncates large payloads correctly.
 * <p>
 * This test uses a very small payload threshold (100 bytes) to force truncation
 * of even simple workflow data.
 * <p>
 * Manual verification: Check test logs for truncated payloads with:
 * - "__truncated__": true
 * - "__originalSize__": [number > 100]
 * - "__preview__": "[first 50 characters]"
 */
@QuarkusTest
@TestProfile(StructuredLoggingTruncationTest.EnableTruncation.class)
public class StructuredLoggingTruncationTest {

    @Inject
    HelloWorkflow helloWorkflow;

    @Test
    void testStructuredLoggingTruncatesLargePayloads() {
        // HelloWorkflow input is empty {}, but output is {"message": "hello world!"}
        // With 100 byte threshold, the serialized JSON should exceed limit
        helloWorkflow.startInstance().await().indefinitely();

        // Manual verification: Check build logs for JSON events with truncation markers:
        // INFO [io.quarkiverse.flow.structuredlogging] {"eventType":"io.serverlessworkflow.task.completed.v1",
        //   "output":{"__truncated__":true,"__originalSize__":123,"__preview__":"..."}, ...}
    }

    public static class EnableTruncation implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.events", "workflow.*",
                    "quarkus.flow.structured-logging.include-task-payloads", "true",
                    "quarkus.flow.structured-logging.include-workflow-payloads", "true",
                    "quarkus.flow.structured-logging.payload-max-size", "100", // Very small for testing
                    "quarkus.flow.structured-logging.truncate-preview-size", "50",
                    "quarkus.flow.structured-logging.log-level", "INFO");
        }
    }
}
