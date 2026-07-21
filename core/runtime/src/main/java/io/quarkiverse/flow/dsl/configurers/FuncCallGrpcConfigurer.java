package io.quarkiverse.flow.dsl.configurers;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.FuncCallGrpcTaskBuilder;

@FunctionalInterface
public interface FuncCallGrpcConfigurer extends Consumer<FuncCallGrpcTaskBuilder> {
}
