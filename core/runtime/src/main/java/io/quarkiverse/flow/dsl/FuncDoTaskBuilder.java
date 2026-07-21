package io.quarkiverse.flow.dsl;

import java.util.function.Consumer;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncDoFluent;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.serverlessworkflow.fluent.spec.BaseDoTaskBuilder;
import io.serverlessworkflow.fluent.spec.WorkflowTaskBuilder;

public class FuncDoTaskBuilder extends BaseDoTaskBuilder<FuncDoTaskBuilder, FuncTaskItemListBuilder>
        implements FuncTaskTransformations<FuncDoTaskBuilder>,
        ConditionalTaskBuilder<FuncDoTaskBuilder>,
        FuncDoFluent<FuncDoTaskBuilder> {

    public FuncDoTaskBuilder(int listSizeOffset) {
        super(new FuncTaskItemListBuilder(listSizeOffset));
    }

    @Override
    public FuncDoTaskBuilder self() {
        return this;
    }

    @Override
    public FuncDoTaskBuilder emit(String name, Consumer<FuncEmitTaskBuilder> itemsConfigurer) {
        this.listBuilder().emit(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder listen(String name, Consumer<FuncListenTaskBuilder> itemsConfigurer) {
        this.listBuilder().listen(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder raise(String name, Consumer<FuncRaiseTaskBuilder> itemsConfigurer) {
        this.listBuilder().raise(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder forEach(String name, Consumer<FuncForTaskBuilder> itemsConfigurer) {
        this.listBuilder().forEach(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder set(String name, Consumer<FuncSetTaskBuilder> itemsConfigurer) {
        this.listBuilder().set(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder set(String name, String expr) {
        this.listBuilder().set(name, expr);
        return this;
    }

    @Override
    public FuncDoTaskBuilder switchCase(
            String name, Consumer<FuncSwitchTaskBuilder> itemsConfigurer) {
        this.listBuilder().switchCase(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder function(String name, Consumer<FuncCallTaskBuilder> cfg) {
        this.listBuilder().function(name, cfg);
        return this;
    }

    @Override
    public FuncDoTaskBuilder fork(String name, Consumer<FuncForkTaskBuilder> itemsConfigurer) {
        this.listBuilder().fork(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder http(String name, Consumer<FuncCallHttpTaskBuilder> itemsConfigurer) {
        this.listBuilder().http(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder openapi(
            String name, Consumer<FuncCallOpenAPITaskBuilder> itemsConfigurer) {
        this.listBuilder().openapi(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder grpc(String name, Consumer<FuncCallGrpcTaskBuilder> itemsConfigurer) {
        this.listBuilder().grpc(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder workflow(String name, Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        this.listBuilder().workflow(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder subflow(String name, Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        this.listBuilder().subflow(name, itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder subflow(Consumer<WorkflowTaskBuilder> itemsConfigurer) {
        this.listBuilder().subflow(itemsConfigurer);
        return this;
    }

    @Override
    public FuncDoTaskBuilder tryCatch(String name, Consumer<FuncTryTaskBuilder> itemsConfigurer) {
        this.listBuilder().tryCatch(name, itemsConfigurer);
        return this;
    }
}
