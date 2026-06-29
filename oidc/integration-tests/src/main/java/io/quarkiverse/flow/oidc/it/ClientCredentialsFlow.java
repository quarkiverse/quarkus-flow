package io.quarkiverse.flow.oidc.it;

import java.net.URI;
import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

/**
 * Calls a downstream service with no auth declared in the DSL. The {@code Authorization} header is attached
 * by the Flow OIDC decorator (mode {@code client-credentials}, acquiring a token from a named oidc-client).
 */
@ApplicationScoped
public class ClientCredentialsFlow extends Flow {

    @ConfigProperty(name = "downstream.url")
    String downstreamUrl;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("oidc-cc", "quarkus-flow")
                .use(use -> use.authentications(auth -> {
                }))
                .tasks(FuncDSL.wait(Duration.ofMillis(500)),
                        FuncDSL.call(
                                FuncDSL.http("callClientCredentials")
                                        .GET()
                                        .uri(URI.create(downstreamUrl + "/cc/resource"),
                                                FuncDSL.use("cc"))))
                .build();
    }
}
