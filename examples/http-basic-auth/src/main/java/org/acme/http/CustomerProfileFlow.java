package org.acme.http;

import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;
import static io.quarkiverse.flow.dsl.FlowDSL.get;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.basic;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.secret;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CustomerProfileFlow extends Flow {

    @ConfigProperty(name = "demo.server")
    String securedServer;

    @Override
    public Workflow descriptor() {
        URI endpoint = URI.create(securedServer + "/secure/profile");

        return workflow("secure-customer-profile").use(secret("demo"))
                .tasks(get(endpoint, basic("${ $secret.demo.username }", "${ $secret.demo.password }"))).build();
    }
}
