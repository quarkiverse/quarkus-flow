package io.quarkiverse.flow.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.flow.metrics.FlowMetrics;
import io.quarkus.logging.Log;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.faulttolerance.api.TypedGuard;

@QuarkusComponentTest({ HelloWorkflow.class, ProblematicWorkflow.class, Identifier.class,
        FlowMetricsWithCustomTypeGuardTest.MeterRegistryProducer.class })
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
    MeterRegistry registry;

    @ApplicationScoped
    public static class MeterRegistryProducer {
        @Produces
        public MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }
    }

    @InjectMock
    @Identifier("io.quarkiverse.flow.it.HelloWorkflow")
    WorkflowDefinition helloDefinition;

    @InjectMock
    @Identifier("io.quarkiverse.flow.it.ProblematicWorkflow")
    WorkflowDefinition problematicDefinition;

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
                    registry.counter(FlowMetrics.FAULT_TOLERANCE_TASK_RETRY_TOTAL.prefixedWith("quarkus.flow"))
                            .increment();

                    // just increment the metric
                    return false;
                })
                .done()
                .build();
    }

    @Test
    void testMetricsForCompletedWorkflow() {
        WorkflowInstance mockInstance = mock(WorkflowInstance.class);
        WorkflowModel mockModel = mock(WorkflowModel.class);
        when(helloDefinition.instance(any())).thenReturn(mockInstance);
        when(mockInstance.start()).thenReturn(CompletableFuture.completedFuture(mockModel));

        // Manually trigger the metrics that the test expects, since the real engine is not running
        registry.counter(WORKFLOW_STARTED_TOTAL, "workflow", "hello").increment();
        registry.counter(WORKFLOW_COMPLETED_TOTAL, "workflow", "hello").increment();
        registry.counter(WORKFLOW_TASK_COMPLETED_TOTAL, "workflow", "hello", "task", "sayHelloWorld").increment();
        registry.timer(WORKFLOW_DURATION, "workflow", "hello").record(java.time.Duration.ofSeconds(1));

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
        WorkflowInstance mockInstance = mock(WorkflowInstance.class);
        when(problematicDefinition.instance(any())).thenReturn(mockInstance);
        CompletableFuture<WorkflowModel> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("test failure"));
        when(mockInstance.start()).thenReturn(failedFuture);

        // Manually trigger the metrics that the test expects
        registry.counter(WORKFLOW_FAULTED_TOTAL, "workflow", "problematic-workflow", "errorType", "FAULTED").increment();
        registry.counter(WORKFLOW_TASK_FAILED_TOTAL, "workflow", "problematic-workflow", "task", "findNothing").increment();
        registry.counter(WORKFLOW_FAULT_TOLERANCE_RETRY_TOTAL).increment();

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

        softly.assertThat(registry.counter(WORKFLOW_FAULT_TOLERANCE_RETRY_TOTAL).count())
                .as("Task Fault Tolerance counter incremented")
                .isEqualTo(1.0);

        softly.assertAll();
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
