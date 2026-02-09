package io.quarkiverse.flow.durable.kube.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.wildfly.common.Assert.assertNotNull;

import java.time.Duration;

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

    @Test
    void workflowApplicationIdMatchesLease() {
        // This will block until the controller fires ACQUIRED (through your event -> coordinator gate)
        String lease = memberLeaseCoordinator.awaitLease(Duration.ofSeconds(30));

        // Force app initialization (injecting usually already does, but calling a method makes it obvious)
        assertNotNull(app);

        // Replace with the actual getter name:
        assertEquals(lease, app.id());
    }

}
