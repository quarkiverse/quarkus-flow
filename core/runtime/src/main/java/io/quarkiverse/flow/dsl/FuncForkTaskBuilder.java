package io.quarkiverse.flow.dsl;

import java.util.function.Function;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.quarkiverse.flow.dsl.types.CallJava;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.fluent.spec.AbstractForkTaskBuilder;
import io.serverlessworkflow.fluent.spec.spi.ForkTaskFluent;

public class FuncForkTaskBuilder
        extends AbstractForkTaskBuilder<FuncForkTaskBuilder, FuncTaskItemListBuilder>
        implements FuncTaskTransformations<FuncForkTaskBuilder>,
        ConditionalTaskBuilder<FuncForkTaskBuilder>,
        ForkTaskFluent<FuncForkTaskBuilder, FuncTaskItemListBuilder> {

    FuncForkTaskBuilder() {
        super();
    }

    @Override
    protected FuncForkTaskBuilder self() {
        return this;
    }

    @Override
    protected FuncTaskItemListBuilder newTaskItemListBuilder(int listOffsetSize) {
        return new FuncTaskItemListBuilder(listOffsetSize);
    }

    public <T, V> FuncForkTaskBuilder branch(String name, Function<T, V> function) {
        return branch(name, function, null);
    }

    public <T, V> FuncForkTaskBuilder branch(
            String name, Function<T, V> function, Class<T> argParam) {
        return branch(name, function, argParam, null);
    }

    public <T, V> FuncForkTaskBuilder branch(
            String name, Function<T, V> function, Class<T> argParam, Class<V> returnClass) {
        this.appendBranch(
                new TaskItem(
                        this.defaultBranchName(name, this.currentOffset()),
                        new Task()
                                .withCallTask(
                                        new CallTask()
                                                .withCallFunction(CallJava.function(function, argParam, returnClass)))));
        return this;
    }

    public <T, V> FuncForkTaskBuilder branch(Function<T, V> function) {
        return this.branch(null, function);
    }
}
