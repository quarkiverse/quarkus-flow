package io.quarkiverse.flow.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class FlowMetricsTest {

    // Metric identifiers from io.quarkiverse.flow.metrics.FlowMetrics with default prefix
    public static final String WORKFLOW_STARTED_TOTAL = "quarkus.flow.workflow.started.total";
    public static final String WORKFLOW_COMPLETED_TOTAL = "quarkus.flow.workflow.completed.total";
    public static final String WORKFLOW_DURATION = "quarkus.flow.workflow.duration";
    public static final String WORKFLOW_FAULTED_TOTAL = "quarkus.flow.workflow.faulted.total";
    public static final String WORKFLOW_TASK_COMPLETED_TOTAL = "quarkus.flow.task.completed.total";
    public static final String WORKFLOW_TASK_FAILED_TOTAL = "quarkus.flow.task.failed.total";

    @Inject
    MeterRegistry registry;

    @Inject
    HelloWorkflow helloWorkflow;

    @Inject
    ProblematicWorkflow problematicWorkflow;

    @Test
    void testMetricsForCompletedWorkflow() {
        SoftAssertions softly = new SoftAssertions();

        helloWorkflow.startInstance().await().indefinitely();

        // Verify "workflow.started.total" counter
        softly.assertThat(registry.counter(WORKFLOW_STARTED_TOTAL, "workflow", "hello").count())
                .as("Workflow started counter")
                .isEqualTo(1.0);

        // Verify "workflow.completed.total" counter
        softly.assertThat(registry.counter(WORKFLOW_COMPLETED_TOTAL, "workflow", "hello").count())
                .as("Workflow completed counter")
                .isEqualTo(1.0);

        // Verify "task.completed.total" counter for the specific task
        softly.assertThat(registry.counter(WORKFLOW_TASK_COMPLETED_TOTAL,
                "workflow", "hello",
                "task", "sayHelloWorld").count())
                .as("Task completed counter")
                .isEqualTo(1.0);

        // Verify "workflow.duration" timer records execution time
        softly.assertThat(registry.find(WORKFLOW_DURATION).timer())
                .as("Workflow duration timer")
                .isNotNull();

        // Finalize assertions
        softly.assertAll();
    }

    @Test
    void testMetricsForFailedWorkflow() {
        SoftAssertions softly = new SoftAssertions();
        // Workflow expected to fail for testing purposes
        try {
            problematicWorkflow.startInstance().await().indefinitely();
        } catch (Exception e) {
            // Expected failure from ProblematicWorkflow
        }

        // Verify "workflow.faulted.total" counter
        // The MicrometerExecutionListener tags failures with the workflow name and errorType
        softly.assertThat(registry.counter(WORKFLOW_FAULTED_TOTAL,
                "workflow", "problematic-workflow",
                "errorType", "FAULTED").count())
                .as("Workflow faulted counter incremented")
                .isEqualTo(1.0);

        // Verify task failure metrics are also captured
        // Since the workflow fails during a task, the task failure counter should also trigger
        softly.assertThat(registry.counter(WORKFLOW_TASK_FAILED_TOTAL,
                "workflow", "problematic-workflow",
                "task", "findNothing").count())
                .as("Task failed counter incremented")
                .isEqualTo(1.0);

        softly.assertAll();
    }

}
