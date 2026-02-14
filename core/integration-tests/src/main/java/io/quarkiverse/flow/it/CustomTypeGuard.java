package io.quarkiverse.flow.it;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.flow.metrics.FlowMetrics;
import io.quarkus.logging.Log;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.faulttolerance.api.TypedGuard;

@ApplicationScoped
public class CustomTypeGuard {

    @Inject
    MeterRegistry meterRegistry;

    @Produces
    @Identifier("custom-type-guard")
    public TypedGuard<CompletionStage<WorkflowModel>> custom() {
        return TypedGuard.<CompletionStage<WorkflowModel>> create(new TypeLiteral<>() {
        })
                .withRetry()
                .whenException(throwable -> {
                    WorkflowException workflowException = (WorkflowException) throwable;
                    Log.info("Handling WorkflowException class: " + workflowException.getWorkflowError());
                    meterRegistry.counter(FlowMetrics.FAULT_TOLERANCE_TASK_RETRY.prefixedWith("quarkus.flow"))
                            .increment();

                    // just increment the metric
                    return false;
                })
                .done()
                .build();
    }
}
