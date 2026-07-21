package io.quarkiverse.flow.dsl;

import io.serverlessworkflow.impl.WorkflowContextData;

/**
 * Functions that expect a {@link WorkflowContextData} injection in runtime
 *
 * @param <T> The task payload input
 * @param <R> The task result output
 */
@FunctionalInterface
public interface CtxBiFunction<T, R> {
    R apply(WorkflowContextData context, T payload);
}
