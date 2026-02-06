package io.quarkiverse.flow.deployment.test.faulttolerance;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.config.FlowHttpConfig;
import io.quarkiverse.flow.providers.FaultToleranceProvider;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.faulttolerance.api.TypedGuard;

public class FaultToleranceProviderTest {

    @Test
    void should_create_type_guard_with_retry_enabled_and_five_retries() {

        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .build().getConfigMapping(FlowHttpConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut.guardFor("any", "any");

        AtomicInteger atomicInteger = new AtomicInteger(0);

        CompletionStage<WorkflowModel> stage = typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
            atomicInteger.incrementAndGet();
            throw new RuntimeException("Intentional");
        }));

        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException ignored) {
        }

        assertThat(atomicInteger.get()).isEqualTo(4);
    }

    @Test
    void should_not_retry_when_retry_is_disabled() {

        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.retry-enabled", "false")
                .build().getConfigMapping(FlowHttpConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut.guardFor("any", "any");

        AtomicInteger atomicInteger = new AtomicInteger(0);

        CompletionStage<WorkflowModel> stage = typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
            atomicInteger.incrementAndGet();
            throw new RuntimeException("Intentional");
        }));

        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException ignored) {
        }

        assertThat(atomicInteger.get()).isEqualTo(1);
    }

    @Test
    void should_retry_one_time_more_when_max_retries_is_one() {

        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.retry-enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.max-retries", "1")
                .build().getConfigMapping(FlowHttpConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut.guardFor("any", "any");

        AtomicInteger atomicInteger = new AtomicInteger(0);

        CompletionStage<WorkflowModel> stage = typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
            atomicInteger.incrementAndGet();
            throw new RuntimeException("Intentional");
        }));

        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException ignored) {
        }

        assertThat(atomicInteger.get()).isEqualTo(2);
    }

    @Test
    void should_disable_retry_for_workflow_level() {

        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.retry-enabled", "true") // default is true
                .withDefaultValue("quarkus.flow.http.client.max-retries", "1")
                .withDefaultValue("quarkus.flow.http.client.workflow.transfer.name", "transferClient")
                .withDefaultValue("quarkus.flow.http.client.named.transferClient.retry-enabled", "false")
                .build().getConfigMapping(FlowHttpConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut.guardFor("transfer", "any");

        AtomicInteger atomicInteger = new AtomicInteger(0);

        CompletionStage<WorkflowModel> stage = typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
            atomicInteger.incrementAndGet();
            throw new RuntimeException("Intentional");
        }));

        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException ignored) {
        }

        assertThat(atomicInteger.get()).isEqualTo(1);
    }

    @Test
    void should_configure_the_max_retries_at_task_level() {

        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.retry-enabled", "true") // default is true
                .withDefaultValue("quarkus.flow.http.client.max-retries", "1")
                .withDefaultValue("quarkus.flow.http.client.workflow.transfer.task.notify.name", "transferNotifier")
                .withDefaultValue("quarkus.flow.http.client.workflow.transfer.name", "transferNotifierWorkflowLevel") // workflow-level
                .withDefaultValue("quarkus.flow.http.client.named.transferNotifierWorkflowLevel.retry-enabled", "false")
                .withDefaultValue("quarkus.flow.http.client.named.transferNotifier.retry-enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.named.transferNotifier.max-retries", "2") // overrides the default
                .build().getConfigMapping(FlowHttpConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut.guardFor("transfer", "notify");

        AtomicInteger atomicInteger = new AtomicInteger(0);

        CompletionStage<WorkflowModel> stage = typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
            atomicInteger.incrementAndGet();
            throw new RuntimeException("Intentional");
        }));

        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException ignored) {
        }

        assertThat(atomicInteger.get()).isEqualTo(3);
    }

    @Test
    void should_apply_delay_between_retries() {

        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.retry-enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.max-retries", "2")
                .withDefaultValue("quarkus.flow.http.client.delay", "1s")
                .build()
                .getConfigMapping(FlowHttpConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig);

        TypedGuard<CompletionStage<WorkflowModel>> guard = sut.guardFor("transfer", "notify");

        List<Long> executionTimes = new CopyOnWriteArrayList<>();

        CompletionStage<WorkflowModel> stage = guard.get(() -> CompletableFuture.supplyAsync(() -> {
            executionTimes.add(System.nanoTime());
            throw new RuntimeException("Intentional");
        }));

        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException ignored) {
        }

        assertThat(executionTimes.size()).isEqualTo(3);

        long secondDelayMs = TimeUnit.NANOSECONDS.toMillis(
                executionTimes.get(2) - executionTimes.get(1));

        long toleranceMs = 200; // safe margin to avoid flaky tests
        assertThat(secondDelayMs).isGreaterThanOrEqualTo(1000 - toleranceMs);
    }

}
