package io.quarkiverse.flow.it;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowDSL;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Emits a CloudEvent without declaring an explicit {@code source}, so that Quarkus Flow injects a default source
 * derived from the workflow identity ({@code namespace:name:version}).
 */
@ApplicationScoped
public class OrderEmitterWorkflow extends Flow {

    public static final String NAME = "order-emitter";
    public static final String NAMESPACE = "org.acme.events";

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow(NAME, NAMESPACE)
                .tasks(FlowDSL.emitJson("com.acme.order.placed.v1", Map.class))
                .build();
    }
}
