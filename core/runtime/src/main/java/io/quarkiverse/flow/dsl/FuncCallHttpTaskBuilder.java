package io.quarkiverse.flow.dsl;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.HTTPArguments;
import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;
import io.serverlessworkflow.fluent.spec.spi.CallHttpTaskFluent;

public class FuncCallHttpTaskBuilder extends TaskBaseBuilder<FuncCallHttpTaskBuilder>
        implements CallHttpTaskFluent<FuncCallHttpTaskBuilder>,
        FuncTaskTransformations<FuncCallHttpTaskBuilder>,
        ConditionalTaskBuilder<FuncCallHttpTaskBuilder> {

    FuncCallHttpTaskBuilder() {
        super.setTask(new CallHTTP().withWith(new HTTPArguments()));
    }

    @Override
    public FuncCallHttpTaskBuilder self() {
        return this;
    }
}
