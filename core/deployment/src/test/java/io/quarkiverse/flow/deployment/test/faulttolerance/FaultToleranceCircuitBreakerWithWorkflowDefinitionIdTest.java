package io.quarkiverse.flow.deployment.test.faulttolerance;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.config.FlowHttpConfig;
import io.quarkiverse.flow.config.FlowMetricsConfig;
import io.quarkiverse.flow.providers.FaultToleranceProvider;
import io.quarkiverse.flow.providers.WorkflowTaskContext;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.faulttolerance.api.TypedGuard;

public class FaultToleranceCircuitBreakerWithWorkflowDefinitionIdTest {

    @Test
    void should_open_circuit_breaker_after_threshold_failures() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "3")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "1.0")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        WorkflowDefinitionId workflowId = new WorkflowDefinitionId("org.acme", "payment", "1.0.0");
        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext(workflowId, "process", true));

        AtomicInteger callCount = new AtomicInteger(0);
        for (int i = 0; i < 4; i++) {
            CompletionStage<WorkflowModel> stage = typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            }));
            try {
                stage.toCompletableFuture().join();
            } catch (RuntimeException ignored) {
            }
        }
        assertThat(callCount.get()).isEqualTo(3);
    }
}
