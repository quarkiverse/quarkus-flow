package io.quarkiverse.flow.dsl;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.types.ContextPredicate;
import io.quarkiverse.flow.dsl.types.FilterPredicate;
import io.serverlessworkflow.fluent.spec.dsl.BaseListenSpec;

public abstract class BaseFuncListenSpec<SELF, LB>
        extends BaseListenSpec<SELF, LB, FuncListenToBuilder, FuncEventFilterBuilder> {

    protected BaseFuncListenSpec(ToInvoker<LB, FuncListenToBuilder> toInvoker) {
        super(
                toInvoker,
                // allApplier
                (tb, filters) -> tb.all(castFilters(filters)),
                // anyApplier
                (tb, filters) -> tb.any(castFilters(filters)),
                // oneApplier
                FuncListenToBuilder::one);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<FuncEventFilterBuilder>[] castFilters(Consumer<?>[] arr) {
        return (Consumer<FuncEventFilterBuilder>[]) arr;
    }

    public <T> SELF until(Predicate<T> predicate, Class<T> predClass) {
        Objects.requireNonNull(predicate, "predicate");
        this.setUntilStep(u -> u.until(predicate, predClass));
        return self();
    }

    public <T> SELF until(ContextPredicate<T> predicate, Class<T> predClass) {
        Objects.requireNonNull(predicate, "predicate");
        this.setUntilStep(u -> u.until(predicate, predClass));
        return self();
    }

    public <T> SELF until(FilterPredicate<T> predicate, Class<T> predClass) {
        Objects.requireNonNull(predicate, "predicate");
        this.setUntilStep(u -> u.until(predicate, predClass));
        return self();
    }
}
