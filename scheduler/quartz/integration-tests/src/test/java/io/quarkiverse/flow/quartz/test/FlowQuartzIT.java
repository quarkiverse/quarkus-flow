package io.quarkiverse.flow.quartz.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

@QuarkusTest
public class FlowQuartzIT {
    @Inject
    @Identifier("test:after-driven-schedule:0.1.0")
    WorkflowDefinition afterStartDefinition;

    @Inject
    @Identifier("test:cron-driven-schedule:0.1.0")
    WorkflowDefinition cronDefinition;

    @Inject
    @Identifier("test:every-driven-schedule:0.1.0")
    WorkflowDefinition everyDefinition;

    @Test
    void testAfter() {
        afterStartDefinition.instance(Map.of()).start().join();
        assertThat(afterStartDefinition.scheduledInstances().isEmpty());
        // Allow extra time for CI with parallel builds
        await()
                .pollDelay(Duration.ofMillis(50))
                .pollInterval(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(2))
                .until(() -> !afterStartDefinition.scheduledInstances().isEmpty());
    }

    @Test
    void testEvery() {
        // Workflow is scheduled every 1 second
        // VERY generous timeout for CI: scheduler threads can be completely starved
        // under heavy parallel builds, especially on resource-constrained CI runners
        await()
                .pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(30))
                .until(() -> everyDefinition.scheduledInstances().size() >= 1);
        // Wait for second instance (1s interval + massive buffer for CI delays)
        await()
                .pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(30))
                .until(() -> everyDefinition.scheduledInstances().size() >= 2);
    }

    @Test
    void testCron() {
        await()
                .atMost(Duration.ofSeconds(1))
                .until(() -> cronDefinition.scheduledInstances().size() >= 1);
    }
}
