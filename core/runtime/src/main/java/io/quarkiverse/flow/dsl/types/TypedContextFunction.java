package io.quarkiverse.flow.dsl.types;

public record TypedContextFunction<T, V>(ContextFunction<T, V> function, Class<T> argClass)
        implements
            FilterSerializable {
}
