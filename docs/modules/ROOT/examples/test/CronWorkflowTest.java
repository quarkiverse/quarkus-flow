package test;

import static org.awaitility.Awaitility.await;

import java.time.Duration;

import jakarta.inject.Inject;

import org.acme.CronWorkflow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

@QuarkusTest
public class CronWorkflowTest {

    @Inject
    CronWorkflow cronWorkflow;

    @Inject
    @Identifier("org.acme.CronWorkflow")
    WorkflowDefinition cronFlowDefinition;

    @Test
    void testCronWorkflow() {
        // Check that the cron in descriptor is not replaced
        Assertions.assertSame("* * * * * ?", cronWorkflow.descriptor().getSchedule().getCron());

        // Wait for at least 3 scheduled instances to be created
        await()
                .pollInterval(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> Assertions.assertTrue(cronFlowDefinition.scheduledInstances().size() > 2));
    }
}
