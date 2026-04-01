package org.acme.flow.durable.kube;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestDemoWorkflow {

    @Inject
    DemoWorkflow workflow;

    @Test
    public void testWorkflow() {
        Optional<Map<String, Object>> output = workflow.startInstance(Map.of()).await().atMost(Duration.ofSeconds(10)).asMap();
        assertTrue(output.isPresent());
        assertEquals("OK", output.get().get("httpResult"));
    }
}
