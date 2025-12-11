package io.quarkiverse.flow.camel;

import io.serverlessworkflow.fluent.func.dsl.FuncCallStep;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

public final class FlowCamelDSL {

    private FlowCamelDSL() {
    }

    public static <T, R> FuncCallStep<T, R> camel(CamelConnector<T, R> fn, Class<T> clazz) {
        return FuncDSL.function(fn, clazz);
    }

}
