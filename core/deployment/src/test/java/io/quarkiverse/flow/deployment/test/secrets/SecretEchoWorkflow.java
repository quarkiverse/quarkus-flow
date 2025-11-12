package io.quarkiverse.flow.deployment.test.secrets;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class SecretEchoWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return WorkflowBuilder
                .workflow()
                .use(u -> u.secrets("mySecret")) // refer to the credentials provider via secret name
                .tasks(t -> t.set("${ $secret.mySecret.password }")) // will resolve to "s3cr3t!"
                .build();
    }
}
