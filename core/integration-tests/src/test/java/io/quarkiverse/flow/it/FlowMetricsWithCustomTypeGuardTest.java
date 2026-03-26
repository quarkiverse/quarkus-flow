package io.quarkiverse.flow.it;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Metrics;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.faulttolerance.api.TypedGuard;

@QuarkusTest
@TestProfile(FlowMetricsWithCustomTypeGuardTest.FaultToleranceProfile.class)
public class FlowMetricsWithCustomTypeGuardTest {

    // Metric identifiers from io.quarkiverse.flow.metrics.FlowMetrics with default prefix
    public static final String WORKFLOW_STARTED_TOTAL = "quarkus.flow.workflow.started.total";
    public static final String WORKFLOW_COMPLETED_TOTAL = "quarkus.flow.workflow.completed.total";
    public static final String WORKFLOW_DURATION = "quarkus.flow.workflow.duration";
    public static final String WORKFLOW_FAULTED_TOTAL = "quarkus.flow.workflow.faulted.total";
    public static final String WORKFLOW_TASK_COMPLETED_TOTAL = "quarkus.flow.task.completed.total";
    public static final String WORKFLOW_TASK_FAILED_TOTAL = "quarkus.flow.task.failed.total";
    public static final String WORKFLOW_FAULT_TOLERANCE_RETRY_TOTAL = "quarkus.flow.fault.tolerance.task.retry.total";

    @Inject
    HelloWorkflow helloWorkflow;

    @Inject
    ProblematicWorkflow problematicWorkflow;

    @Produces
    @Identifier("custom-type-guard")
    public TypedGuard<CompletionStage<WorkflowModel>> custom() {
        return TypedGuard.<CompletionStage<WorkflowModel>> create(new TypeLiteral<>() {
        })
                .withRetry()
                .whenException(throwable -> {
                    WorkflowException workflowException = (WorkflowException) throwable;
                    Log.info("Handling WorkflowException class: " + workflowException.getWorkflowError());

                    // Trigger retries so the platform can record retry metrics.
                    return true;
                })
                .done()
                .build();
    }

    @Test
    void testMetricsForCompletedWorkflow() {
        double startedBefore = counterCount(WORKFLOW_STARTED_TOTAL, "workflow", "hello");
        double completedBefore = counterCount(WORKFLOW_COMPLETED_TOTAL, "workflow", "hello");
        double taskCompletedBefore = counterCount(WORKFLOW_TASK_COMPLETED_TOTAL, "workflow", "hello", "task",
                "sayHelloWorld");
        long durationCountBefore = timerCount(WORKFLOW_DURATION, "workflow", "hello");

        SoftAssertions softly = new SoftAssertions();

        helloWorkflow.startInstance().await().indefinitely();

        softly.assertThat(counterCount(WORKFLOW_STARTED_TOTAL, "workflow", "hello"))
                .as("Workflow started counter")
                .isEqualTo(startedBefore + 1.0);

        softly.assertThat(counterCount(WORKFLOW_COMPLETED_TOTAL, "workflow", "hello"))
                .as("Workflow completed counter")
                .isEqualTo(completedBefore + 1.0);

        softly.assertThat(counterCount(WORKFLOW_TASK_COMPLETED_TOTAL,
                "workflow", "hello",
                "task", "sayHelloWorld"))
                .as("Task completed counter")
                .isEqualTo(taskCompletedBefore + 1.0);

        softly.assertThat(Metrics.globalRegistry.find(WORKFLOW_DURATION).tags("workflow", "hello").timer())
                .as("Workflow duration timer")
                .isNotNull();
        softly.assertThat(timerCount(WORKFLOW_DURATION, "workflow", "hello"))
                .as("Workflow duration timer count")
                .isEqualTo(durationCountBefore + 1);

        softly.assertAll();
    }

    @Test
    void testMetricsForFailedWorkflow() {
        double faultedBefore = counterCount(WORKFLOW_FAULTED_TOTAL, "workflow", "problematic-workflow",
                "errorType", "FAULTED");
        double taskFailedBefore = counterCount(WORKFLOW_TASK_FAILED_TOTAL, "workflow", "problematic-workflow", "task",
                "findNothing");

        SoftAssertions softly = new SoftAssertions();
        try {
            problematicWorkflow.startInstance().await().indefinitely();
        } catch (Exception e) {
            Log.info("Workflow failed as expected for metrics assertion: " + e.getMessage());
        }

        softly.assertThat(counterCount(WORKFLOW_FAULTED_TOTAL,
                "workflow", "problematic-workflow",
                "errorType", "FAULTED"))
                .as("Workflow faulted counter incremented")
                .isGreaterThanOrEqualTo(faultedBefore + 1.0);

        softly.assertThat(counterCount(WORKFLOW_TASK_FAILED_TOTAL,
                "workflow", "problematic-workflow",
                "task", "findNothing"))
                .as("Task failed counter incremented")
                .isGreaterThanOrEqualTo(taskFailedBefore + 1.0);

        softly.assertAll();
    }

    private double counterCount(String name, String... tags) {
        var counter = Metrics.globalRegistry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private long timerCount(String name, String... tags) {
        var timer = Metrics.globalRegistry.find(name).tags(tags).timer();
        return timer == null ? 0L : timer.count();
    }

    public static class FaultToleranceProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.http.client.workflow.problematic-workflow.name", "custom-type-guard",
                    "quarkus.flow.http.client.named.custom-type-guard.resilience.identifier", "custom-type-guard");
        }
    }
}
