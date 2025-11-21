package org.acme.example;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.basic;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.secret;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class CustomerProfileFlow extends Flow {

    @ConfigProperty(name = "demo.server", defaultValue = "http://localhost:8080")
    String securedServer;

    @Override
    public Workflow descriptor() {
        URI endpoint = URI.create(securedServer + "/secure/profile");

        return workflow("secure-customer-profile")
                // Load secrets into workflow data (see secrets documentation)
                .use(secret("demo"))
                .tasks(
                        // GET with HTTP Basic credentials taken from the secret
                        get(endpoint, basic("${ $secret.demo.username }", "${ $secret.demo.password }")))
                .build();
    }
}
