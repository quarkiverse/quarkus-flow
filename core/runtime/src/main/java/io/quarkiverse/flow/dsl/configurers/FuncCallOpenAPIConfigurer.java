package io.quarkiverse.flow.dsl.configurers;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.FuncCallOpenAPITaskBuilder;

@FunctionalInterface
public interface FuncCallOpenAPIConfigurer extends Consumer<FuncCallOpenAPITaskBuilder> {
}
