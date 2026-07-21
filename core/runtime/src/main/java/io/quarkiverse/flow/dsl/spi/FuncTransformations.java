package io.quarkiverse.flow.dsl.spi;

import java.util.function.Function;

import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.TypedContextFunction;
import io.quarkiverse.flow.dsl.types.TypedFilterFunction;
import io.quarkiverse.flow.dsl.types.TypedFunction;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.InputFrom;
import io.serverlessworkflow.api.types.Output;
import io.serverlessworkflow.api.types.OutputAs;
import io.serverlessworkflow.fluent.spec.spi.TransformationHandlers;

public interface FuncTransformations<SELF extends FuncTransformations<SELF>>
        extends TransformationHandlers {

    @SuppressWarnings("unchecked")
    default <T, V> SELF inputFrom(Function<T, V> function) {
        setInput(new Input().withFrom(new InputFrom().withObject(function)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF inputFrom(Function<T, V> function, Class<T> argClass) {
        setInput(
                new Input().withFrom(new InputFrom().withObject(new TypedFunction<>(function, argClass))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF inputFrom(FilterFunction<T, V> function) {
        setInput(new Input().withFrom(new InputFrom().withObject(function)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF inputFrom(FilterFunction<T, V> function, Class<T> argClass) {
        setInput(
                new Input()
                        .withFrom(new InputFrom().withObject(new TypedFilterFunction<>(function, argClass))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF inputFrom(ContextFunction<T, V> function) {
        setInput(new Input().withFrom(new InputFrom().withObject((function))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF inputFrom(ContextFunction<T, V> function, Class<T> argClass) {
        setInput(
                new Input()
                        .withFrom(new InputFrom().withObject(new TypedContextFunction<>(function, argClass))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default SELF inputFrom(String jqExpression) {
        setInput(new Input().withFrom(new InputFrom().withString(jqExpression)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF outputAs(Function<T, V> function) {
        setOutput(new Output().withAs(new OutputAs().withObject(function)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF outputAs(Function<T, V> function, Class<T> argClass) {
        setOutput(
                new Output().withAs(new OutputAs().withObject(new TypedFunction<>(function, argClass))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF outputAs(FilterFunction<T, V> function) {
        setOutput(new Output().withAs(new OutputAs().withObject(function)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF outputAs(FilterFunction<T, V> function, Class<T> argClass) {
        setOutput(
                new Output()
                        .withAs(new OutputAs().withObject(new TypedFilterFunction<>(function, argClass))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF outputAs(ContextFunction<T, V> function) {
        setOutput(new Output().withAs(new OutputAs().withObject(function)));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T, V> SELF outputAs(ContextFunction<T, V> function, Class<T> argClass) {
        setOutput(
                new Output()
                        .withAs(new OutputAs().withObject(new TypedContextFunction<>(function, argClass))));
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default SELF outputAs(String jqExpression) {
        setOutput(new Output().withAs(new OutputAs().withString(jqExpression)));
        return (SELF) this;
    }
}
