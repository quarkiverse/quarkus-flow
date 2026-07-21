package io.quarkiverse.flow.dsl.types;

public record TypedFilterFunction<T, V>(FilterFunction<T, V> function, Class<T> argClass)
        implements
            FilterSerializable {
}
