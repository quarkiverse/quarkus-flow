package io.quarkiverse.flow.dsl.configurers;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.FuncEmitTaskBuilder;

@FunctionalInterface
public interface FuncEmitConfigurer extends Consumer<FuncEmitTaskBuilder> {
}
