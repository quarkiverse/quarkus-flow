package io.quarkiverse.flow.dsl;

import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.types.ContextPredicate;
import io.quarkiverse.flow.dsl.types.FilterPredicate;
import io.quarkiverse.flow.dsl.types.utils.TaskPredicate;
import io.quarkiverse.flow.dsl.types.utils.TypesUtils;
import io.serverlessworkflow.api.types.AllEventConsumptionStrategy;
import io.serverlessworkflow.api.types.AnyEventConsumptionStrategy;
import io.serverlessworkflow.api.types.ListenTask;
import io.serverlessworkflow.api.types.ListenTo;
import io.serverlessworkflow.api.types.OneEventConsumptionStrategy;
import io.serverlessworkflow.api.types.Until;
import io.serverlessworkflow.fluent.spec.AbstractEventConsumptionStrategyBuilder;

public class FuncListenToBuilder
        extends AbstractEventConsumptionStrategyBuilder<FuncListenToBuilder, ListenTo, FuncEventFilterBuilder> {

    private final ListenTo listenTo = new ListenTo();
    private final ListenTask listenTask;

    public FuncListenToBuilder(ListenTask listenTask) {
        this.listenTask = listenTask;
    }

    @Override
    protected FuncEventFilterBuilder newEventFilterBuilder() {
        return new FuncEventFilterBuilder();
    }

    // TODO: move these methods to default on an interface

    @Override
    protected void setOne(OneEventConsumptionStrategy strategy) {
        this.listenTo.setOneEventConsumptionStrategy(strategy);
    }

    @Override
    protected void setAll(AllEventConsumptionStrategy strategy) {
        this.listenTo.setAllEventConsumptionStrategy(strategy);
    }

    @Override
    protected void setAny(AnyEventConsumptionStrategy strategy) {
        this.listenTo.setAnyEventConsumptionStrategy(strategy);
    }

    @Override
    protected ListenTo getEventConsumptionStrategy() {
        return this.listenTo;
    }

    @Override
    protected void setUntilForAny(Until until) {
        this.listenTo.getAnyEventConsumptionStrategy().setUntil(until);
    }

    public <T> FuncListenToBuilder until(Predicate<T> predicate, Class<T> predClass) {
        TaskPredicate.withPredicate(listenTask, TypesUtils.UNTIL_PRED_NAME, predicate, predClass);
        return this;
    }

    public <T> FuncListenToBuilder until(ContextPredicate<T> predicate, Class<T> predClass) {
        TaskPredicate.withPredicate(listenTask, TypesUtils.UNTIL_PRED_NAME, predicate, predClass);
        return this;
    }

    public <T> FuncListenToBuilder until(FilterPredicate<T> predicate, Class<T> predClass) {
        TaskPredicate.withPredicate(listenTask, TypesUtils.UNTIL_PRED_NAME, predicate, predClass);
        return this;
    }
}
