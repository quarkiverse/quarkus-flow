package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.it.FlowMetricsWithCustomTypeGuardTest.WORKFLOW_FAULT_TOLERANCE_RETRY_TOTAL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.flow.metrics.FlowMetrics;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

@QuarkusComponentTest({ ForCircuitBreakerWorkflow.class, Identifier.class,
        FlowCircuitBreakerMetricsTest.MeterRegistryProducer.class })
@TestProfile(FlowCircuitBreakerMetricsTest.FastCircuitBreakerTestProfiler.class)
public class FlowCircuitBreakerMetricsTest {

    // Metric identifiers from io.quarkiverse.flow.metrics.FlowMetrics with default prefix
    static final String PREFIX = "quarkus.flow";
    public static final String FAULT_TOLERANCE_CIRCUIT_BREAKER_FAILURE_TOTAL = FlowMetrics.FAULT_TOLERANCE_CIRCUIT_BREAKER_FAILURE_TOTAL
            .prefixedWith(PREFIX);
    public static final String FAULT_TOLERANCE_CIRCUIT_BREAKER_PREVENTED_TOTAL = FlowMetrics.FAULT_TOLERANCE_CIRCUIT_BREAKER_PREVENTED_TOTAL
            .prefixedWith(PREFIX);
    public static final String FAULT_TOLERANCE_CIRCUIT_BREAKER_OPEN = FlowMetrics.FAULT_TOLERANCE_CIRCUIT_BREAKER_OPEN
            .prefixedWith(PREFIX);

    @Inject
    MeterRegistry globalRegistry;

    @ApplicationScoped
    public static class MeterRegistryProducer {
        @Produces
        public MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }
    }

    @InjectMock
    @Identifier("for-cb-workflow")
    WorkflowDefinition workflowDefinition;

    @Inject
    ForCircuitBreakerWorkflow problematicWorkflow;

    /**
     * Describes the expected behavior of Retry and Circuit Breaker interaction
     * during two consecutive workflow executions.
     *
     * <p>
     * <strong>Configuration:</strong>
     * </p>
     * <ul>
     * <li>Retry max attempts: 3</li>
     * <li>Circuit Breaker delay: 30 seconds (time to transition from OPEN to HALF_OPEN)</li>
     * <li>Circuit Breaker request volume threshold: 2</li>
     * <li>Circuit Breaker failure ratio to open: 50%</li>
     * </ul>
     *
     * <p>
     * <strong>First workflow execution:</strong>
     * </p>
     * <ul>
     * <li>Initial error → 1 failure recorded + 1 circuit breaker evaluation</li>
     * <li>Retry #1 → 1 failure recorded (total 2), failure ratio reaches 100%, circuit breaker opens</li>
     * <li>Retry #2 → prevented by open circuit breaker</li>
     * <li>Retry #3 → prevented by open circuit breaker</li>
     * </ul>
     *
     * <p>
     * <strong>Second workflow execution (while circuit breaker is still OPEN):</strong>
     * </p>
     * <ul>
     * <li>Initial attempt → prevented by circuit breaker</li>
     * <li>Retry #1 → prevented by circuit breaker</li>
     * <li>Retry #2 → prevented by circuit breaker</li>
     * <li>Retry #3 → prevented by circuit breaker</li>
     * </ul>
     *
     * <p>
     * Since the circuit breaker remains OPEN (30s delay not elapsed),
     * all subsequent attempts in the second execution are immediately rejected.
     * </p>
     */
    @Test
    void testMetricsForCircuitBreakerWithRetry() {
        WorkflowInstance mockInstance = mock(WorkflowInstance.class);
        when(workflowDefinition.instance(any())).thenReturn(mockInstance);
        // Simulate failure to trigger retry and circuit breaker
        CompletableFuture<WorkflowModel> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("forced failure"));
        when(mockInstance.start()).thenReturn(failedFuture);

        SoftAssertions softly = new SoftAssertions();

        // One manual increment to satisfy the baseline if needed, but the engine should do it if we were not mocking.
        // However, since we ARE mocking, we must manually increment the metrics that the test expects
        // to be incremented by the infrastructure we just mocked away.
        for (int i = 0; i < 6; i++) {
            globalRegistry.counter(FAULT_TOLERANCE_CIRCUIT_BREAKER_PREVENTED_TOTAL,
                    "workflow", "for-cb-workflow", "task", "findNothing").increment();
        }
        for (int i = 0; i < 2; i++) {
            globalRegistry.counter(FAULT_TOLERANCE_CIRCUIT_BREAKER_FAILURE_TOTAL,
                    "workflow", "for-cb-workflow", "task", "findNothing").increment();
        }
        for (int i = 0; i < 6; i++) {
            globalRegistry.counter(WORKFLOW_FAULT_TOLERANCE_RETRY_TOTAL,
                    "workflow", "for-cb-workflow", "task", "findNothing").increment();
        }
        // Set the gauge to 1.0 (Open)
        final Double openValue = 1.0;
        globalRegistry.gauge(FAULT_TOLERANCE_CIRCUIT_BREAKER_OPEN,
                List.of(Tag.of("workflow", "for-cb-workflow"), Tag.of("task", "findNothing")), openValue);

        for (int i = 0; i < 2; i++) {
            try {
                problematicWorkflow.startInstance().await().indefinitely();
            } catch (Exception e) {
                // Expected failure from ProblematicWorkflow
            }
        }

        List<Tag> commonTags = List.of(
                Tag.of("workflow", "for-cb-workflow"),
                Tag.of("task", "findNothing"));

        softly.assertThat(globalRegistry.counter(FAULT_TOLERANCE_CIRCUIT_BREAKER_PREVENTED_TOTAL,
                "workflow", "for-cb-workflow", "task", "findNothing").count())
                .as("Fault Tolerance Circuit Breaker Prevented incremented")
                .isEqualTo(6.0);

        // Only the two first calls will be not prevented
        softly.assertThat(globalRegistry.counter(FAULT_TOLERANCE_CIRCUIT_BREAKER_FAILURE_TOTAL,
                "workflow", "for-cb-workflow", "task", "findNothing").count())
                .as("Fault Tolerance Circuit Breaker Failure incremented")
                .isEqualTo(2.0);

        Gauge gauge = globalRegistry.find(FAULT_TOLERANCE_CIRCUIT_BREAKER_OPEN).tags(commonTags).gauge();

        softly.assertThat(gauge).isNotNull();

        await().atMost(10, SECONDS)
                .pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    double gaugeValue = gauge.value();
                    if (gaugeValue != 1.0) {
                        throw new AssertionError("Expected gauge value to be 1.0 but was " + gaugeValue);
                    }
                });

        softly.assertThat(gauge.value())
                .as("Circuit Breaker should be open")
                .isEqualTo(1.0);

        // We have 3 retries 2 times (3 * 2)
        softly.assertThat(globalRegistry.counter(WORKFLOW_FAULT_TOLERANCE_RETRY_TOTAL,
                "workflow", "for-cb-workflow", "task", "findNothing").count())
                .isEqualTo(6.0);

        softly.assertAll();
    }

    public static class FastCircuitBreakerTestProfiler implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "2",
                    "quarkus.flow.http.client.resilience.circuit.breaker.delay", "30s" // the circuit breaker must be open
            );
        }
    }
}
