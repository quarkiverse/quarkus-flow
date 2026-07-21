package io.quarkiverse.flow.dsl;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.OpenAPIArguments;
import io.serverlessworkflow.api.types.WithOpenAPIParameters;
import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;
import io.serverlessworkflow.fluent.spec.spi.CallOpenAPITaskFluent;

public class FuncCallOpenAPITaskBuilder extends TaskBaseBuilder<FuncCallOpenAPITaskBuilder>
        implements CallOpenAPITaskFluent<FuncCallOpenAPITaskBuilder>,
        FuncTaskTransformations<FuncCallOpenAPITaskBuilder>,
        ConditionalTaskBuilder<FuncCallOpenAPITaskBuilder> {

    FuncCallOpenAPITaskBuilder() {
        final CallOpenAPI callOpenAPI = new CallOpenAPI();
        callOpenAPI.setWith(new OpenAPIArguments().withParameters(new WithOpenAPIParameters()));
        super.setTask(callOpenAPI);
    }

    @Override
    public FuncCallOpenAPITaskBuilder self() {
        return this;
    }
}
