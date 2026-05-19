package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ForEachWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("foreach-workflow")
                .tasks(
                        forEach(OrdersPayload::orders,
                                tasks(
                                        post("$item.id",
                                                wiremockUrl + "/process-order")
                                                .exportAsTaskOutput())))
                .build();
    }
}
