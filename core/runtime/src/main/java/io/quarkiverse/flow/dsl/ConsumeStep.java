package io.quarkiverse.flow.dsl;

import java.util.function.Consumer;

public final class ConsumeStep<T> extends Step<ConsumeStep<T>, FuncCallTaskBuilder> {
    private final String name; // may be null
    private final Consumer<T> consumer;
    private final Class<T> argClass; // may be null

    ConsumeStep(Consumer<T> consumer, Class<T> argClass) {
        this(null, consumer, argClass);
    }

    ConsumeStep(String name, Consumer<T> consumer, Class<T> argClass) {
        this.name = name;
        this.consumer = consumer;
        this.argClass = argClass;
    }

    @Override
    protected void configure(FuncTaskItemListBuilder list, Consumer<FuncCallTaskBuilder> post) {
        if (name == null) {
            list.function(
                    cb -> {
                        // prefer the typed consumer if your builder supports it; otherwise fallback:
                        if (argClass != null)
                            cb.consumer(consumer, argClass);
                        else
                            cb.consumer(consumer);
                        post.accept(cb);
                    });
        } else {
            list.function(
                    name,
                    cb -> {
                        if (argClass != null)
                            cb.consumer(consumer, argClass);
                        else
                            cb.consumer(consumer);
                        post.accept(cb);
                    });
        }
    }
}
