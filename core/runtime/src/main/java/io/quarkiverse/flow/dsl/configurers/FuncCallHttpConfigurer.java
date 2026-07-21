package io.quarkiverse.flow.dsl.configurers;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.FuncCallHttpTaskBuilder;

@FunctionalInterface
public interface FuncCallHttpConfigurer extends Consumer<FuncCallHttpTaskBuilder> {
}
