package io.quarkiverse.flow.dsl;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.serverlessworkflow.api.types.CallGRPC;
import io.serverlessworkflow.api.types.GRPCArguments;
import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;
import io.serverlessworkflow.fluent.spec.spi.CallGrpcTaskFluent;

public class FuncCallGrpcTaskBuilder extends TaskBaseBuilder<FuncCallGrpcTaskBuilder>
        implements CallGrpcTaskFluent<FuncCallGrpcTaskBuilder>,
        FuncTaskTransformations<FuncCallGrpcTaskBuilder>,
        ConditionalTaskBuilder<FuncCallGrpcTaskBuilder> {

    FuncCallGrpcTaskBuilder() {
        final CallGRPC callGRPC = new CallGRPC();
        callGRPC.setWith(new GRPCArguments());
        super.setTask(callGRPC);
    }

    @Override
    public FuncCallGrpcTaskBuilder self() {
        return this;
    }
}
