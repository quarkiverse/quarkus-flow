package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class ForEachWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("foreach-workflow")
                .tasks(
                        forEach(OrdersPayload::orders,
                                tasks(
                                        post("$item.id",
                                                wiremockUrl + "/process-order")
                                                .exportAsTaskOutput())))
                .build();
    }
}
