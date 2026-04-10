package test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
        long start = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - start;
        while (elapsed < 4000) {
            elapsed = System.currentTimeMillis() - start;
        }

        // Check that the cron in descriptor is not replaced
        Assertions.assertSame("* * * * * ?", cronWorkflow.descriptor().getSchedule().getCron());

        // At least 2 should be run, since the test is using 4 second wait
        Assertions.assertTrue(cronFlowDefinition.scheduledInstances().size() > 2);
    }
}
