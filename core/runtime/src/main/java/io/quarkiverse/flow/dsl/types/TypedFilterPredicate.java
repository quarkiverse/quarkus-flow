package io.quarkiverse.flow.dsl.types;

public record TypedFilterPredicate<T>(FilterPredicate<T> predicate, Class<T> argClass)
        implements
            FilterSerializable {
}
