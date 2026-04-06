package org.acme.flow.durable.kube;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class DemoWorkflowIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoWorkflowIT.class);

    @Inject
    DemoWorkflow workflow;

    @Test
    public void testWorkflow() {
        Optional<Map<String, Object>> output = workflow.startInstance(Map.of()).await().atMost(Duration.ofSeconds(5))
                .asMap();
        assertTrue(output.isPresent());
        assertEquals("OK", output.get().get("httpResult"));
        assertNotNull(output.get().get("durationMillis"));
        LOGGER.info("Duration millis is {}", output.get().get("durationMillis"));
    }
}
