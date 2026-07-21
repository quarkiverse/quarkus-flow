package io.quarkiverse.flow.dsl;

import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.quarkiverse.flow.dsl.types.CallJava;
import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.SerializableFunction;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;

public class FuncCallTaskBuilder extends TaskBaseBuilder<FuncCallTaskBuilder>
        implements FuncTaskTransformations<FuncCallTaskBuilder>,
        ConditionalTaskBuilder<FuncCallTaskBuilder> {

    private CallTask callTaskJava;

    FuncCallTaskBuilder() {
    }

    @Override
    protected FuncCallTaskBuilder self() {
        return this;
    }

    public <T, V> FuncCallTaskBuilder function(SerializableFunction<T, V> function) {
        return function(function, null);
    }

    public <T, V> FuncCallTaskBuilder function(Function<T, V> function, Class<T> argClass) {
        return function(function, argClass, null);
    }

    public <T, V> FuncCallTaskBuilder function(
            Function<T, V> function, Class<T> argClass, Class<V> returnClass) {
        this.callTaskJava = new CallTask().withCallFunction(CallJava.function(function, argClass, returnClass));
        super.setTask(this.callTaskJava.getCallFunction());
        return this;
    }

    public <T, V> FuncCallTaskBuilder function(ContextFunction<T, V> function) {
        return function(function, null);
    }

    public <T, V> FuncCallTaskBuilder function(ContextFunction<T, V> function, Class<T> argClass) {
        return function(function, argClass, null);
    }

    public <T, V> FuncCallTaskBuilder function(
            ContextFunction<T, V> function, Class<T> argClass, Class<V> returnClass) {
        this.callTaskJava = new CallTask().withCallFunction(CallJava.function(function, argClass, returnClass));
        super.setTask(this.callTaskJava.getCallFunction());
        return this;
    }

    public <T, V> FuncCallTaskBuilder function(FilterFunction<T, V> function) {
        return function(function, null);
    }

    public <T, V> FuncCallTaskBuilder function(FilterFunction<T, V> function, Class<T> argClass) {
        return function(function, argClass, null);
    }

    public <T, V> FuncCallTaskBuilder function(
            FilterFunction<T, V> function, Class<T> argClass, Class<V> outputClass) {
        this.callTaskJava = new CallTask().withCallFunction(CallJava.function(function, argClass, outputClass));
        super.setTask(this.callTaskJava.getCallFunction());
        return this;
    }

    /** Accept a side-effect Consumer; engine should pass input through unchanged. */
    public <T> FuncCallTaskBuilder consumer(Consumer<T> consumer) {
        this.callTaskJava = new CallTask().withCallFunction(CallJava.consumer(consumer));
        super.setTask(this.callTaskJava.getCallFunction());
        return this;
    }

    /** Accept a Consumer with explicit input type hint. */
    public <T> FuncCallTaskBuilder consumer(Consumer<T> consumer, Class<T> argClass) {
        this.callTaskJava = new CallTask().withCallFunction(CallJava.consumer(consumer, argClass));
        super.setTask(this.callTaskJava.getCallFunction());
        return this;
    }

    public CallTask build() {
        if (this.callTaskJava == null) {
            throw new IllegalStateException(
                    "Call task is not configured. Call function(...) or consumer(...) before build().");
        }
        return this.callTaskJava;
    }
}
