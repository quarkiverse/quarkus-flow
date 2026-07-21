package io.quarkiverse.flow.dsl.executors;

import java.util.function.Function;

import io.serverlessworkflow.impl.ServicePriority;

public interface DataTypeConverter<T, V> extends Function<T, V>, ServicePriority {
    Class<T> sourceType();

    Class<V> targetType();
}
