package io.quarkiverse.flow.dsl.types;

import java.util.function.Function;

public record TypedFunction<T, V>(Function<T, V> function, Class<T> argClass)
        implements
            FilterSerializable {
}
