package io.quarkiverse.flow.grpc.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Unremovable;
import io.quarkus.grpc.GrpcClient;

@ApplicationScoped
@Unremovable
public class GreeterClientHolder {

    @GrpcClient("flowGrpc")
    MutinyGreeterGrpc.MutinyGreeterStub stub;
}
