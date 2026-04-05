package io.quarkiverse.flow.durable.kube.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
public class GreetingsFlowIT {

    @Inject
    GreetingsFlow flow;

    @Test
    void testGreetingsFlowExecutesSuccessfully() throws Exception {
        WorkflowModel finalState = flow.instance(Map.of()).start().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertNotNull(finalState, "Workflow should return a final state model");
        assertEquals("Hello Human!", finalState.asMap().orElse(Map.of()).get("message"));
    }
}
