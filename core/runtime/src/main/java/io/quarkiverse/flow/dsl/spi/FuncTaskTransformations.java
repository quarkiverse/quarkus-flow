package io.quarkiverse.flow.dsl.spi;

import java.util.function.Function;

import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.TypedContextFunction;
import io.quarkiverse.flow.dsl.types.TypedFilterFunction;
import io.quarkiverse.flow.dsl.types.TypedFunction;
import io.serverlessworkflow.api.types.Export;
import io.serverlessworkflow.api.types.ExportAs;
import io.serverlessworkflow.fluent.spec.spi.TaskTransformationHandlers;

public interface FuncTaskTransformations<SELF extends FuncTaskTransformations<SELF>>
        extends TaskTransformationHandlers, FuncTransformations<SELF> {

    @SuppressWarnings("unchecked")
    default <T, V> SELF exportAs(Function<T, V> function) {
        setExport(new Export().withAs(new ExportAs().withObject(function)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF exportAs(Function<T, V> function, Class<T> argClass) {
        setExport(
                new Export()
                        .withAs(new ExportAs().withObject(new TypedFunction<T, V>(function, argClass))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF exportAs(FilterFunction<T, V> function) {
        setExport(new Export().withAs(new ExportAs().withObject(function)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF exportAs(FilterFunction<T, V> function, Class<T> argClass) {
        setExport(
                new Export()
                        .withAs(new ExportAs().withObject(new TypedFilterFunction<>(function, argClass))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF exportAs(ContextFunction<T, V> function) {
        setExport(new Export().withAs(new ExportAs().withObject(function)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF exportAs(ContextFunction<T, V> function, Class<T> argClass) {
        setExport(
                new Export()
                        .withAs(new ExportAs().withObject(new TypedContextFunction<>(function, argClass))));
        return (SELF) this;
    }
}
