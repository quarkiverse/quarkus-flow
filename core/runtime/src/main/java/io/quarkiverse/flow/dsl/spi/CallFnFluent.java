package io.quarkiverse.flow.dsl.spi;

import java.util.function.Consumer;

import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;

public interface CallFnFluent<SELF extends TaskBaseBuilder<?>, LIST> {

    LIST function(String name, Consumer<SELF> cfg);

    default LIST function(Consumer<SELF> cfg) {
        return this.function(null, cfg);
    }
}
