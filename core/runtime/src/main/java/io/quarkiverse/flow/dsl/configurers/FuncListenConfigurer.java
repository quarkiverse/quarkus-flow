package io.quarkiverse.flow.dsl.configurers;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.FuncListenTaskBuilder;

@FunctionalInterface
public interface FuncListenConfigurer extends Consumer<FuncListenTaskBuilder> {
}
