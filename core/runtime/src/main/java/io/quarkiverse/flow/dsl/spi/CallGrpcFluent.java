package io.quarkiverse.flow.dsl.spi;

import java.util.function.Consumer;

import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;

public interface CallGrpcFluent<SELF extends TaskBaseBuilder<SELF>, LIST> {

    LIST grpc(String name, Consumer<SELF> itemsConfigurer);

    default LIST grpc(Consumer<SELF> itemsConfigurer) {
        return this.grpc(null, itemsConfigurer);
    }
}
