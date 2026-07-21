package io.quarkiverse.flow.dsl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.quarkiverse.flow.dsl.types.CallJava;
import io.quarkiverse.flow.dsl.types.LoopFunction;
import io.quarkiverse.flow.dsl.types.LoopPredicate;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndex;
import io.quarkiverse.flow.dsl.types.utils.ForTaskFunction;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;
import io.serverlessworkflow.fluent.spec.spi.ForEachTaskFluent;

public class FuncForTaskBuilder extends TaskBaseBuilder<FuncForTaskBuilder>
        implements FuncTaskTransformations<FuncForTaskBuilder>,
        ConditionalTaskBuilder<FuncForTaskBuilder>,
        ForEachTaskFluent<FuncForTaskBuilder, FuncTaskItemListBuilder> {

    private final ForTask forTask;
    private final List<TaskItem> items;

    FuncForTaskBuilder() {
        this.forTask = new ForTask();
        this.forTask.withFor(new ForTaskConfiguration());
        this.items = new ArrayList<>();
        super.setTask(forTask);
    }

    @Override
    protected FuncForTaskBuilder self() {
        return this;
    }

    public <T, V> FuncForTaskBuilder whileC(LoopPredicate<T, V> predicate) {
        ForTaskFunction.withWhile(forTask, predicate);
        return this;
    }

    public <T, V> FuncForTaskBuilder whileC(LoopPredicateIndex<T, V> predicate) {
        ForTaskFunction.withWhile(forTask, predicate);
        return this;
    }

    public <T, V> FuncForTaskBuilder collection(Function<T, Collection<V>> collectionF) {
        ForTaskFunction.withCollection(forTask, collectionF);
        return this;
    }

    public <T, V> FuncForTaskBuilder collection(
            Function<T, Collection<V>> collectionF, Class<T> clazz) {
        ForTaskFunction.withCollection(forTask, collectionF, clazz);
        return this;
    }

    public <T, V, R> FuncForTaskBuilder tasks(String name, LoopFunction<T, V, R> function) {
        if (name == null || name.isBlank()) {
            name = "for-task-" + this.items.size();
        }
        this.items.add(
                new TaskItem(
                        name,
                        new Task()
                                .withCallTask(
                                        new CallTask()
                                                .withCallFunction(
                                                        CallJava.loopFunction(function, this.forTask.getFor().getEach())))));
        return this;
    }

    public <T, V, R> FuncForTaskBuilder tasks(LoopFunction<T, V, R> function) {
        return this.tasks(null, function);
    }

    @Override
    public FuncForTaskBuilder each(String each) {
        this.forTask.getFor().withEach(each);
        return this;
    }

    @Override
    public FuncForTaskBuilder in(String in) {
        this.forTask.getFor().withIn(in);
        return this;
    }

    @Override
    public FuncForTaskBuilder at(String at) {
        this.forTask.getFor().withAt(at);
        return this;
    }

    @Override
    public FuncForTaskBuilder whileC(String expression) {
        this.forTask.setWhile(expression);
        return this;
    }

    public FuncForTaskBuilder tasks(Consumer<FuncTaskItemListBuilder> consumer) {
        final FuncTaskItemListBuilder builder = new FuncTaskItemListBuilder(this.items.size());
        consumer.accept(builder);
        this.items.addAll(builder.build());
        return this;
    }

    public ForTask build() {
        this.forTask.setDo(this.items);
        return this.forTask;
    }
}
