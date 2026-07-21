package io.quarkiverse.flow.dsl.types;

import java.util.function.Predicate;

public record TypedPredicate<T>(Predicate<T> pred, Class<T> argClass)
        implements
            FilterSerializable {
}
