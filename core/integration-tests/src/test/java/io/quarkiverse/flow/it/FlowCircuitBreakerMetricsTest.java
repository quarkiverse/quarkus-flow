package io.quarkiverse.flow.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Metrics;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(FlowCircuitBreakerMetricsTest.FastCircuitBreakerTestProfiler.class)
public class FlowCircuitBreakerMetricsTest {

    private static final String WORKFLOW = "for-cb-workflow";
    private static final String TASK = "findNothing";

    private static final String FT_TASK_RETRY_TOTAL = "quarkus.flow.fault.tolerance.task.retry.total";
    private static final String FT_CB_PREVENTED_TOTAL = "quarkus.flow.fault.tolerance.circuit.breaker.prevented.total";
    private static final String FT_CB_FAILURE_TOTAL = "quarkus.flow.fault.tolerance.circuit.breaker.failure.total";
    private static final String FT_CB_OPEN = "quarkus.flow.fault.tolerance.circuit.breaker.open";

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
        double retryBefore = counterCount(FT_TASK_RETRY_TOTAL);
        double cbPreventedBefore = counterCount(FT_CB_PREVENTED_TOTAL);
        double cbFailureBefore = counterCount(FT_CB_FAILURE_TOTAL);
        double cbOpenBefore = gaugeValue(FT_CB_OPEN);

        Throwable first = captureFailure();
        assertThat(first).as("First execution should fail").isNotNull();

        // Validate we observed real Fault Tolerance behavior via exported metrics.
        assertThat(counterCount(FT_TASK_RETRY_TOTAL))
                .as("Retry metric should increase on failures that trigger retry")
                .isGreaterThanOrEqualTo(retryBefore + 1.0);
        assertThat(counterCount(FT_CB_FAILURE_TOTAL))
                .as("Circuit breaker failure metric should increase on failures observed by the breaker")
                .isGreaterThanOrEqualTo(cbFailureBefore + 1.0);

        // With request-volume-threshold=2 and failure-ratio=0.5, two consecutive failures should open the breaker.
        assertThat(gaugeValue(FT_CB_OPEN))
                .as("Circuit breaker should be open after the first execution")
                .isGreaterThanOrEqualTo(Math.max(1.0, cbOpenBefore));

        double cbPreventedAfterFirst = counterCount(FT_CB_PREVENTED_TOTAL);

        Throwable second = captureFailure();
        // SmallRye FT throws a CircuitBreakerOpen* exception when prevented; keep the check string-based
        // to avoid binding this test to a specific exception type/version.
        assertThat(containsAnyInCausalChain(second, "CircuitBreakerOpen", "Circuit breaker", "OPEN"))
                .as("Second execution should be prevented by an open circuit breaker")
                .isTrue();

        assertThat(counterCount(FT_CB_PREVENTED_TOTAL))
                .as("Circuit breaker prevented metric should increase when breaker blocks executions")
                .isGreaterThanOrEqualTo(cbPreventedAfterFirst + 1.0);

        // Prevented execution should fail quickly (no outbound call).
        await().atMost(10, SECONDS).untilAsserted(() -> {
            long start = System.nanoTime();
            captureFailure();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertThat(elapsedMs).isLessThan(2000);
        });
    }

    private Throwable captureFailure() {
        try {
            problematicWorkflow.startInstance().await().indefinitely();
            throw new AssertionError("Expected workflow to fail");
        } catch (Throwable t) {
            return t;
        }
    }

    private boolean containsAnyInCausalChain(Throwable t, String... tokens) {
        Throwable cur = t;
        while (cur != null) {
            String name = cur.getClass().getName();
            String message = cur.getMessage();
            for (String token : tokens) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                if (name != null && name.contains(token)) {
                    return true;
                }
                if (message != null && message.contains(token)) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private String dumpCausalChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int i = 0;
        while (cur != null && i < 20) {
            if (i > 0) {
                sb.append("\ncaused by: ");
            }
            sb.append(cur.getClass().getName());
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                sb.append(": ").append(cur.getMessage());
            }
            cur = cur.getCause();
            i++;
        }
        return sb.toString();
    }

    private double counterCount(String name) {
        var counter = Metrics.globalRegistry.find(name)
                .tags("workflow", WORKFLOW, "task", TASK)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double gaugeValue(String name) {
        var gauge = Metrics.globalRegistry.find(name)
                .tags("workflow", WORKFLOW, "task", TASK)
                .gauge();
        return gauge == null ? 0.0 : gauge.value();
    }

    public static class FastCircuitBreakerTestProfiler implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold", "2",
                    "quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio", "0.5",
                    "quarkus.flow.http.client.resilience.retry.max-retries", "3",
                    "quarkus.flow.http.client.resilience.circuit.breaker.delay", "30s" // the circuit breaker must be open
            );
        }
    }
}
