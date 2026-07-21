package io.quarkiverse.flow.dsl.types;

/**
 * Functions that expect a workflow instance ID injection in runtime
 *
 * @param <T> The task payload input
 * @param <R> The task result output
 */
@FunctionalInterface
public interface InstanceIdFunction<T, R> extends FunctionObject {
    R apply(String instanceId, T payload);
}
