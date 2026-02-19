package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.it.FlowMetricsWithCustomTypeGuardTest.WORKFLOW_FAULT_TOLERANCE_RETRY_TOTAL;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.openapi;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.metrics.FlowMetrics;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@QuarkusTest
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
        SoftAssertions softly = new SoftAssertions();

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

        softly.assertThat(globalRegistry.counter(FAULT_TOLERANCE_CIRCUIT_BREAKER_PREVENTED_TOTAL, commonTags).count())
                .as("Fault Tolerance Circuit Breaker Prevented incremented")
                .isEqualTo(6.0);

        // Only the two first calls will be not prevented
        softly.assertThat(globalRegistry.counter(FAULT_TOLERANCE_CIRCUIT_BREAKER_FAILURE_TOTAL, commonTags).count())
                .as("Fault Tolerance Circuit Breaker Failure incremented")
                .isEqualTo(2.0);

        Gauge gauge = globalRegistry.find(FAULT_TOLERANCE_CIRCUIT_BREAKER_OPEN).tags(commonTags).gauge();

        softly.assertThat(gauge).isNotNull();

        // The Circuit Breaker should be kept opened
        softly.assertThat(gauge.value())
                .isEqualTo(1);

        // We have 3 retries 2 times (3 * 2)
        softly.assertThat(globalRegistry.counter(WORKFLOW_FAULT_TOLERANCE_RETRY_TOTAL, commonTags).count())
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

    @ApplicationScoped
    static class ForCircuitBreakerWorkflow extends Flow {

        @Override
        public Workflow descriptor() {
            final URI problematic = URI.create("openapi/problematic.json");

            return FuncWorkflowBuilder.workflow("for-cb-workflow")
                    // You find the operation in the spec file, field operationId.
                    .tasks(openapi("findNothing").document(problematic).operation("getProblematic")
                            // We use a jq expression to select from the JSON array the first item after the task response.
                            .outputAs("${{ message }}")) // the code will fail before for testing ExceptionMapper
                    .build();
        }

    }
}
