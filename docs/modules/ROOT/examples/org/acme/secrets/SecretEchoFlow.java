package org.acme.secrets;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SecretEchoFlow extends Flow {
    @Override
    public Workflow descriptor() {
        return workflow()
                .use(u -> u.secrets("mySecret")) // declare the handle
                .tasks(t -> t.set("${ $secret.mySecret.password }")) // -> "s3cr3t!"
                .build();
    }
}
