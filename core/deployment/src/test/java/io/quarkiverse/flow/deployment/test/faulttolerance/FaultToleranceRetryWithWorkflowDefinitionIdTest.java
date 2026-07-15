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

public class FaultToleranceRetryWithWorkflowDefinitionIdTest {

    @Test
    void should_use_workflow_definition_id_for_retry_workflow_key() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.max-retries", "3")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        WorkflowDefinitionId workflowId = new WorkflowDefinitionId("org.acme", "transfer", "0.0.1");
        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext(workflowId, "notify", true));

        AtomicInteger atomicInteger = new AtomicInteger(0);
        CompletionStage<WorkflowModel> stage = typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
            atomicInteger.incrementAndGet();
            throw new RuntimeException("Simulated failure");
        }));

        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException ignored) {
        }
        assertThat(atomicInteger.get()).isEqualTo(4);
    }

    @Test
    void should_use_workflow_id_routing_for_retry() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.max-retries", "3")
                .withDefaultValue("quarkus.flow.http.client.client.\"workflow.transfer.task.notify\".name", "legacyRetryClient")
                .withDefaultValue("quarkus.flow.http.client.named.legacyRetryClient.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.named.legacyRetryClient.resilience.retry.max-retries", "2")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        WorkflowDefinitionId workflowId = new WorkflowDefinitionId("org.acme", "transfer", "0.0.1");
        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext(workflowId, "notify", true));

        AtomicInteger atomicInteger = new AtomicInteger(0);
        CompletionStage<WorkflowModel> stage = typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
            atomicInteger.incrementAndGet();
            throw new RuntimeException("Simulated failure");
        }));

        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException ignored) {
        }
        assertThat(atomicInteger.get()).isEqualTo(4);
    }
}
