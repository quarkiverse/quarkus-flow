package io.quarkiverse.flow.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.serverlessworkflow.fluent.spec.configurers.AuthenticationConfigurer;
import io.serverlessworkflow.fluent.spec.spi.CallGrpcTaskFluent;

public class FuncCallGrpcStep extends Step<FuncCallGrpcStep, FuncCallGrpcTaskBuilder> {

    private final List<Consumer<CallGrpcTaskFluent<?>>> steps = new ArrayList<>();

    private String name;

    public FuncCallGrpcStep(String name) {
        this.name = name;
    }

    public FuncCallGrpcStep() {
    }

    public void setName(String name) {
        this.name = name;
    }

    public FuncCallGrpcStep proto(String uri) {
        steps.add(b -> b.proto(uri));
        return this;
    }

    public FuncCallGrpcStep proto(String uri, AuthenticationConfigurer authenticationConfigurer) {
        steps.add(b -> b.proto(uri, authenticationConfigurer));
        return this;
    }

    public FuncCallGrpcStep service(String name, String host) {
        steps.add(b -> b.service(name, host));
        return this;
    }

    public FuncCallGrpcStep service(String name, String host, int port) {
        steps.add(b -> b.service(name, host, port));
        return this;
    }

    public FuncCallGrpcStep method(String method) {
        steps.add(b -> b.method(method));
        return this;
    }

    public FuncCallGrpcStep argument(String name, Object value) {
        steps.add(b -> b.argument(name, value));
        return this;
    }

    public FuncCallGrpcStep arguments(java.util.Map<String, Object> arguments) {
        steps.add(b -> b.arguments(arguments));
        return this;
    }

    public FuncCallGrpcStep authentication(AuthenticationConfigurer authenticationConfigurer) {
        steps.add(b -> b.authentication(authenticationConfigurer));
        return this;
    }

    @Override
    protected void configure(FuncTaskItemListBuilder list, Consumer<FuncCallGrpcTaskBuilder> post) {
        list.grpc(
                name,
                builder -> {
                    for (Consumer<CallGrpcTaskFluent<?>> c : steps) {
                        c.accept(builder);
                    }
                    post.accept(builder);
                });
    }
}
