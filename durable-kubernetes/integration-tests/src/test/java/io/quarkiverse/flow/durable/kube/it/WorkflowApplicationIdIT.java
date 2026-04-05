package io.quarkiverse.flow.durable.kube.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.wildfly.common.Assert.assertNotNull;
import static org.wildfly.common.Assert.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.durable.kube.MemberLeaseCoordinator;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowApplication;

@QuarkusTest
public class WorkflowApplicationIdIT {

    @Inject
    WorkflowApplication app;

    @Inject
    MemberLeaseCoordinator memberLeaseCoordinator;

    @Inject
    GreetingsFlow flow;

    @Test
    void workflowApplicationIdMatchesLease() {
        // This will block until the controller fires ACQUIRED
        String lease = memberLeaseCoordinator.awaitLease(Duration.ofSeconds(30));

        // Force app initialization
        assertNotNull(app);

        // Replace with the actual getter name:
        assertEquals(lease, app.id());

        Optional<Map<String, Object>> output = flow.instance(Map.of()).start().join().asMap();
        assertNotNull(output);
        assertTrue(output.isPresent());
        assertNotNull(output.orElse(Map.of()).get("message"));
    }

}
