package io.quarkiverse.flow.scheduler.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

@QuarkusTest
public class FlowSchedulerTest {
    @Inject
    @Identifier("test:after-driven-schedule")
    WorkflowDefinition afterStartDefinition;

    @Inject
    @Identifier("test:cron-driven-schedule")
    WorkflowDefinition cronDefinition;

    @Inject
    @Identifier("test:every-driven-schedule")
    WorkflowDefinition everyDefinition;

    @Test
    void testAfter() throws IOException, InterruptedException, ExecutionException {
        afterStartDefinition.instance(Map.of()).start().join();
        assertThat(afterStartDefinition.scheduledInstances().isEmpty());
        await()
                .pollDelay(Duration.ofMillis(50))
                .atMost(Duration.ofMillis(500))
                .until(() -> afterStartDefinition.scheduledInstances().size() >= 1);
    }

    @Test
    void testEvery() throws IOException, InterruptedException, ExecutionException {
        await()
                .atMost(Duration.ofSeconds(1).plus(Duration.ofMillis(200)))
                .until(() -> everyDefinition.scheduledInstances().size() == 1);
        await()
                .atMost(Duration.ofSeconds(1).plus(Duration.ofMillis(200)))
                .until(() -> everyDefinition.scheduledInstances().size() == 2);
    }

    @Test
    @Disabled
    void testCron() throws IOException, InterruptedException, ExecutionException {
        await()
                .atMost(Duration.ofMinutes(1).plus(Duration.ofSeconds(10)))
                .until(() -> cronDefinition.scheduledInstances().size() == 1);
    }
}
