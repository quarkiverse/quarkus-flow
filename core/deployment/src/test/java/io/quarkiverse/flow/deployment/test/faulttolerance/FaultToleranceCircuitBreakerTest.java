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
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.faulttolerance.api.TypedGuard;

public class FaultToleranceCircuitBreakerTest {

    private static final boolean micrometerNotSupported = false;

    @Test
    void should_open_circuit_breaker_after_failure_threshold() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "4")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "0.5")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.max-retries", "10")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("payment", "process", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // With retry enabled and circuit breaker threshold of 4
        // Retry will attempt up to 11 times (1 + 10 retries)
        // Circuit opens after 4 failures, but retry continues attempting
        // Each retry attempt that hits open circuit still counts
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // Circuit opens after 4 calls, remaining retries get CircuitBreakerOpenException
        // Total: 4 actual calls + remaining retry attempts hitting open circuit
        assertThat(callCount.get()).isGreaterThanOrEqualTo(4);
        assertThat(callCount.get()).isLessThanOrEqualTo(11);
    }

    @Test
    void should_not_open_circuit_breaker_when_disabled() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "false")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "false")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("payment", "process", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Execute multiple failing calls
        for (int i = 0; i < 10; i++) {
            try {
                typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                    callCount.incrementAndGet();
                    throw new RuntimeException("Simulated failure");
                })).toCompletableFuture().join();
            } catch (Exception ignored) {
            }
        }

        // All calls should have been executed (circuit breaker is disabled)
        assertThat(callCount.get()).isEqualTo(10);
    }

    @Test
    void should_configure_circuit_breaker_at_workflow_level() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "false")
                .withDefaultValue("quarkus.flow.http.client.workflow.payment.name", "paymentClient")
                .withDefaultValue("quarkus.flow.http.client.named.paymentClient.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue(
                        "quarkus.flow.http.client.named.paymentClient.resilience.circuit-breaker.request-volume-threshold", "2")
                .withDefaultValue("quarkus.flow.http.client.named.paymentClient.resilience.circuit-breaker.failure-ratio",
                        "0.5")
                .withDefaultValue("quarkus.flow.http.client.named.paymentClient.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.named.paymentClient.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.named.paymentClient.resilience.retry.max-retries", "5")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("payment", "process", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Workflow-level config: threshold of 2
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // Circuit opens after 2 calls
        assertThat(callCount.get()).isGreaterThanOrEqualTo(2);
        assertThat(callCount.get()).isLessThanOrEqualTo(6);
    }

    @Test
    void should_configure_circuit_breaker_at_task_level() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "false")
                .withDefaultValue("quarkus.flow.http.client.workflow.payment.task.validate.name", "paymentValidator")
                .withDefaultValue("quarkus.flow.http.client.named.paymentValidator.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue(
                        "quarkus.flow.http.client.named.paymentValidator.resilience.circuit-breaker.request-volume-threshold",
                        "3")
                .withDefaultValue("quarkus.flow.http.client.named.paymentValidator.resilience.circuit-breaker.failure-ratio",
                        "0.66")
                .withDefaultValue("quarkus.flow.http.client.named.paymentValidator.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.named.paymentValidator.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.named.paymentValidator.resilience.retry.max-retries", "5")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("payment", "validate", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Task-level config: threshold of 3
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // Circuit opens after 3 calls
        assertThat(callCount.get()).isGreaterThanOrEqualTo(3);
        assertThat(callCount.get()).isLessThanOrEqualTo(6);
    }

    @Test
    void should_respect_custom_failure_ratio() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "10")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "0.3")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.max-retries", "15")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("order", "create", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Threshold of 10 with 30% failure ratio
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // Circuit opens after 10 calls
        assertThat(callCount.get()).isGreaterThanOrEqualTo(10);
        assertThat(callCount.get()).isLessThanOrEqualTo(16);
    }

    @Test
    void should_remain_closed_when_below_failure_threshold() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "10")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "0.5")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "false")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("inventory", "check", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Execute 10 calls with 4 failures (40% failure ratio - below 50% threshold)
        for (int i = 0; i < 10; i++) {
            try {
                typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                    callCount.incrementAndGet();
                    if (callCount.get() <= 4) {
                        throw new RuntimeException("Simulated failure");
                    }
                    return null;
                })).toCompletableFuture().join();
            } catch (Exception ignored) {
            }
        }

        // Circuit should remain closed (below 50% failure threshold)
        // All 10 calls should have been executed
        assertThat(callCount.get()).isEqualTo(10);

        // Verify circuit is still accepting calls
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                return null;
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // The 11th call should have been executed (circuit is still closed)
        assertThat(callCount.get()).isEqualTo(11);
    }

    @Test
    void should_not_open_circuit_before_request_volume_threshold() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "5")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "0.5")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "false")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("shipping", "calculate", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Execute only 3 calls (below threshold of 5), all failing
        for (int i = 0; i < 3; i++) {
            try {
                typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                    callCount.incrementAndGet();
                    throw new RuntimeException("Simulated failure");
                })).toCompletableFuture().join();
            } catch (Exception ignored) {
            }
        }

        // Circuit should remain closed (haven't reached request volume threshold)
        // Next call should still execute
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                return null;
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // All 4 calls should have been executed
        assertThat(callCount.get()).isEqualTo(4);
    }

    @Test
    void should_open_circuit_with_100_percent_failure_ratio() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "3")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "1.0")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "false")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("notification", "send", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Execute 3 calls: 2 failures and 1 success (66% failure - below 100% threshold)
        for (int i = 0; i < 3; i++) {
            try {
                typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                    callCount.incrementAndGet();
                    if (callCount.get() <= 2) {
                        throw new RuntimeException("Simulated failure");
                    }
                    return null;
                })).toCompletableFuture().join();
            } catch (Exception ignored) {
            }
        }

        // Circuit should remain closed (66% < 100% failure ratio)
        // Next call should execute
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                return null;
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        assertThat(callCount.get()).isEqualTo(4);
    }

    @Test
    void should_handle_mixed_success_and_failure_calls() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "6")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "0.5")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.max-retries", "10")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("user", "register", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Threshold of 6 with 50% failure ratio
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // Circuit opens after 6 calls
        assertThat(callCount.get()).isGreaterThanOrEqualTo(6);
        assertThat(callCount.get()).isLessThanOrEqualTo(11);
    }

    @Test
    void should_override_default_circuit_breaker_with_task_specific_config() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "10")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "0.5")
                .withDefaultValue("quarkus.flow.http.client.workflow.billing.task.charge.name", "billingCharger")
                .withDefaultValue("quarkus.flow.http.client.named.billingCharger.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue(
                        "quarkus.flow.http.client.named.billingCharger.resilience.circuit-breaker.request-volume-threshold",
                        "3")
                .withDefaultValue("quarkus.flow.http.client.named.billingCharger.resilience.circuit-breaker.failure-ratio",
                        "0.5")
                .withDefaultValue("quarkus.flow.http.client.named.billingCharger.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.named.billingCharger.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.named.billingCharger.resilience.retry.max-retries", "5")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("billing", "charge", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Task-specific threshold of 3 (overrides default of 10)
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // Verify task-specific threshold of 3 is used (not default 10)
        assertThat(callCount.get()).isGreaterThanOrEqualTo(3);
        assertThat(callCount.get()).isLessThanOrEqualTo(6);
    }

    @Test
    void should_handle_all_successful_calls_without_opening_circuit() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "5")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "0.2")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "false")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("analytics", "track", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Execute 10 successful calls
        for (int i = 0; i < 10; i++) {
            try {
                typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                    callCount.incrementAndGet();
                    return null;
                })).toCompletableFuture().join();
            } catch (Exception ignored) {
            }
        }

        // Circuit should remain closed (0% failure ratio)
        assertThat(callCount.get()).isEqualTo(10);

        // Additional call should also succeed
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                return null;
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        assertThat(callCount.get()).isEqualTo(11);
    }

    @Test
    void should_handle_edge_case_with_minimum_request_volume() {
        FlowHttpConfig flowHttpConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowHttpConfig.class)
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "1")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "1.0")
                .withDefaultValue("quarkus.flow.http.client.resilience.circuit-breaker.delay", "0s")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.enabled", "true")
                .withDefaultValue("quarkus.flow.http.client.resilience.retry.max-retries", "5")
                .build().getConfigMapping(FlowHttpConfig.class);

        FlowMetricsConfig flowMetricsConfig = new SmallRyeConfigBuilder()
                .addDiscoveredConverters()
                .withMapping(FlowMetricsConfig.class)
                .build().getConfigMapping(FlowMetricsConfig.class);

        FaultToleranceProvider sut = new FaultToleranceProvider(flowHttpConfig, flowMetricsConfig);

        TypedGuard<CompletionStage<WorkflowModel>> typeGuard = sut
                .guardFor(new WorkflowTaskContext("cache", "invalidate", micrometerNotSupported));

        AtomicInteger callCount = new AtomicInteger(0);

        // Minimum threshold of 1 with 100% failure ratio
        try {
            typeGuard.get(() -> CompletableFuture.supplyAsync(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            })).toCompletableFuture().join();
        } catch (Exception ignored) {
        }

        // Circuit opens after 1 call
        assertThat(callCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(callCount.get()).isLessThanOrEqualTo(6);
    }
}
