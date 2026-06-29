package io.quarkiverse.flow.oidc.it;

import java.net.URI;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

/**
 * Calls a downstream service with no auth declared in the DSL. The {@code Authorization} header is attached
 * entirely by the Flow OIDC decorator (mode {@code propagation}, scheme matched by workflow name).
 */
@ApplicationScoped
public class PropagationFlow extends Flow {

    @ConfigProperty(name = "downstream.url")
    String downstreamUrl;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("oidc-propagation", "quarkus-flow")
                .use(use -> use.authentications(auth -> {
                }))
                .tasks(FuncDSL.set(Map.of("token", "propagation")),
                        FuncDSL.call(
                                FuncDSL.http("callPropagation")
                                        .GET()
                                        .uri(URI.create(downstreamUrl + "/propagation/resource"),
                                                FuncDSL.use("propagation"))))
                .build();
    }
}
