package org.acme.grpc;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Unremovable;
import io.quarkus.grpc.GrpcClient;

@ApplicationScoped
@Unremovable
public class GrpcClientHolder {

    @GrpcClient("flowGrpc")
    MutinyGreeterGrpc.MutinyGreeterStub stub;
}
