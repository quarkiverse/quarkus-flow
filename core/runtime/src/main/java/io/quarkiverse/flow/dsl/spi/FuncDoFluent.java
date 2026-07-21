package io.quarkiverse.flow.dsl.spi;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.FuncCallGrpcTaskBuilder;
import io.quarkiverse.flow.dsl.FuncCallHttpTaskBuilder;
import io.quarkiverse.flow.dsl.FuncCallOpenAPITaskBuilder;
import io.quarkiverse.flow.dsl.FuncCallTaskBuilder;
import io.quarkiverse.flow.dsl.FuncEmitTaskBuilder;
import io.quarkiverse.flow.dsl.FuncForTaskBuilder;
import io.quarkiverse.flow.dsl.FuncForkTaskBuilder;
import io.quarkiverse.flow.dsl.FuncListenTaskBuilder;
import io.quarkiverse.flow.dsl.FuncRaiseTaskBuilder;
import io.quarkiverse.flow.dsl.FuncSetTaskBuilder;
import io.quarkiverse.flow.dsl.FuncSwitchTaskBuilder;
import io.quarkiverse.flow.dsl.FuncTryTaskBuilder;
import io.serverlessworkflow.fluent.spec.WorkflowTaskBuilder;
import io.serverlessworkflow.fluent.spec.spi.CallHttpFluent;
import io.serverlessworkflow.fluent.spec.spi.CallOpenAPIFluent;
import io.serverlessworkflow.fluent.spec.spi.EmitFluent;
import io.serverlessworkflow.fluent.spec.spi.ForEachFluent;
import io.serverlessworkflow.fluent.spec.spi.ForkFluent;
import io.serverlessworkflow.fluent.spec.spi.ListenFluent;
import io.serverlessworkflow.fluent.spec.spi.RaiseFluent;
import io.serverlessworkflow.fluent.spec.spi.SetFluent;
import io.serverlessworkflow.fluent.spec.spi.SwitchFluent;
import io.serverlessworkflow.fluent.spec.spi.TryCatchFluent;
import io.serverlessworkflow.fluent.spec.spi.WorkflowFluent;

public interface FuncDoFluent<SELF extends FuncDoFluent<SELF>>
        extends SetFluent<FuncSetTaskBuilder, SELF>,
        EmitFluent<FuncEmitTaskBuilder, SELF>,
        ForEachFluent<FuncForTaskBuilder, SELF>,
        SwitchFluent<FuncSwitchTaskBuilder, SELF>,
        ForkFluent<FuncForkTaskBuilder, SELF>,
        ListenFluent<FuncListenTaskBuilder, SELF>,
        RaiseFluent<FuncRaiseTaskBuilder, SELF>,
        TryCatchFluent<FuncTryTaskBuilder, SELF>,
        CallFnFluent<FuncCallTaskBuilder, SELF>,
        CallHttpFluent<FuncCallHttpTaskBuilder, SELF>,
        CallOpenAPIFluent<FuncCallOpenAPITaskBuilder, SELF>,
        CallGrpcFluent<FuncCallGrpcTaskBuilder, SELF>,
        WorkflowFluent<WorkflowTaskBuilder, SELF> {

    default SELF subflow(String name, Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        return this.workflow(name, itemsConfigurer);
    }

    default SELF subflow(Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        return this.workflow(itemsConfigurer);
    }
}
