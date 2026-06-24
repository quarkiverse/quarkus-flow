package org.acme.grpc;

import static io.serverlessworkflow.fluent.spec.dsl.DSL.grpc;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class GrpcGreetingFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return WorkflowBuilder.workflow("grpcGreeting")
                .tasks(tasks -> tasks.grpc("greet",
                        grpc()
                                .proto("proto/greeter.proto")
                                .service("Greeter", "localhost")
                                .method("SayHello")
                                .argument("name", "${ .name }")))
                .build();
    }
}
