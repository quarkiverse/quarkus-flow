package io.quarkiverse.flow.dsl.types;

public record TypedContextPredicate<T>(ContextPredicate<T> predicate, Class<T> argClass)
        implements
            FilterSerializable {
}
