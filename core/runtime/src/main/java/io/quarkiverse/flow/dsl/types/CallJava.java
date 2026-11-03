package io.quarkiverse.flow.dsl.types;

import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkiverse.flow.dsl.types.utils.ReflectionUtils;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.FunctionArguments;
import io.serverlessworkflow.api.types.TaskBase;

public abstract class CallJava {

    private CallJava() {
    }

    public static final String JAVA_CALL_KEY = "Java";
    public static final String FUNCTION_NAME_KEY = "function";
    public static final String INPUT_CLASS_KEY = "inputClass";
    public static final String OUTPUT_CLASS_KEY = "outputClass";
    public static final String VAR_NAME_KEY = "varName";
    public static final String INDEX_NAME_KEY = "index";

    /**
     * Returns the Java function held by the given task, when it is a {@code call: Java} task.
     * <p>
     * The function is the live object the Java DSL captured, so its type tells what kind of call the task is. For
     * example a task created with {@link io.quarkiverse.flow.dsl.FlowDSL#agent(String, UniqueIdBiFunction) agent(..)}
     * holds an {@link AgentFunction}.
     *
     * @param task the task, may be {@code null}
     * @return the function object, or empty when the task is not a Java call or carries no function
     */
    public static Optional<Object> functionOf(TaskBase task) {
        return Optional.ofNullable(task)
                .filter(CallFunction.class::isInstance)
                .map(CallFunction.class::cast)
                .filter(callFunction -> JAVA_CALL_KEY.equals(callFunction.getCall()))
                .map(CallFunction::getWith)
                .map(FunctionArguments::getAdditionalProperties)
                .map(properties -> properties.get(FUNCTION_NAME_KEY));
    }

    private static CallFunction buildFunction(
            Object function, Optional<Class<?>> inputClass, Optional<Class<?>> outputClass) {
        CallFunction result = new CallFunction();
        result.setCall(JAVA_CALL_KEY);
        result.withWith(
                new FunctionArguments()
                        .withAdditionalProperty(FUNCTION_NAME_KEY, function)
                        .withAdditionalProperty(INPUT_CLASS_KEY, inputClass)
                        .withAdditionalProperty(OUTPUT_CLASS_KEY, outputClass));
        return result;
    }

    public static <T> CallFunction consumer(Consumer<T> consumer) {
        return buildFunction(
                consumer,
                ReflectionUtils.methodType(consumer).map(m -> m.parameterType(0)),
                Optional.empty());
    }

    public static <T> CallFunction consumer(Consumer<T> consumer, Class<T> inputClass) {
        return buildFunction(consumer, Optional.ofNullable(inputClass), Optional.empty());
    }

    public static <T, V> CallFunction function(Function<T, V> function) {
        return buildFunction(function, Optional.empty(), Optional.empty());
    }

    public static <T, V> CallFunction function(Function<T, V> function, Class<T> inputClass) {
        return buildFunction(
                function,
                Optional.ofNullable(inputClass),
                ReflectionUtils.methodType(function).map(MethodType::returnType));
    }

    public static <T, V> CallFunction function(
            Function<T, V> function, Class<T> inputClass, Class<V> outputClass) {
        return buildFunction(
                function, Optional.ofNullable(inputClass), Optional.ofNullable(outputClass));
    }

    public static <T, I, V> CallFunction loopFunction(
            LoopFunctionIndex<T, I, V> function, String varName, String indexName) {
        Optional<MethodType> methodType = ReflectionUtils.methodType(function);
        CallFunction result = buildFunction(
                function,
                methodType.map(m -> m.parameterType(0)),
                methodType.map(MethodType::returnType));
        result
                .getWith()
                .withAdditionalProperty(VAR_NAME_KEY, varName)
                .withAdditionalProperty(INDEX_NAME_KEY, indexName);
        return result;
    }

    public static <T, I, V> CallFunction loopFunction(
            LoopFunction<T, I, V> function, String varName) {
        Optional<MethodType> methodType = ReflectionUtils.methodType(function);
        CallFunction result = buildFunction(
                function,
                methodType.map(m -> m.parameterType(0)),
                methodType.map(MethodType::returnType));
        result.getWith().withAdditionalProperty(VAR_NAME_KEY, varName);
        return result;
    }

    public static <V, T> CallFunction function(ContextFunction<T, V> function, Class<T> inputClass) {
        return buildFunction(
                function,
                Optional.ofNullable(inputClass),
                ReflectionUtils.methodType(function).map(MethodType::returnType));
    }

    public static <V, T> CallFunction function(
            ContextFunction<T, V> function, Class<T> inputClass, Class<V> outputClass) {
        return buildFunction(
                function, Optional.ofNullable(inputClass), Optional.ofNullable(outputClass));
    }

    public static <V, T> CallFunction function(FilterFunction<T, V> function, Class<T> inputClass) {
        return buildFunction(
                function,
                Optional.ofNullable(inputClass),
                ReflectionUtils.methodType(function).map(MethodType::returnType));
    }

    public static <V, T> CallFunction function(
            FilterFunction<T, V> function, Class<T> inputClass, Class<V> outputClass) {
        return buildFunction(
                function, Optional.ofNullable(inputClass), Optional.ofNullable(outputClass));
    }
}
