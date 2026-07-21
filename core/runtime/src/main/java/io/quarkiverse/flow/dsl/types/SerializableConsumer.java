package io.quarkiverse.flow.dsl.types;

import java.util.function.Consumer;

@FunctionalInterface
public interface SerializableConsumer<T> extends Consumer<T>, FunctionObject {
}
