package io.quarkiverse.flow.dsl;

import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.quarkiverse.flow.dsl.types.utils.TaskPredicate;
import io.quarkiverse.flow.dsl.types.utils.TypesUtils;
import io.serverlessworkflow.fluent.spec.AbstractListenTaskBuilder;

public class FuncListenTaskBuilder
        extends AbstractListenTaskBuilder<FuncTaskItemListBuilder, FuncListenToBuilder>
        implements ConditionalTaskBuilder<FuncListenTaskBuilder>,
        FuncTaskTransformations<FuncListenTaskBuilder> {

    FuncListenTaskBuilder(FuncTaskItemListBuilder factory) {
        super(factory);
    }

    public <T> FuncListenTaskBuilder until(Predicate<T> predicate, Class<T> predClass) {
        TaskPredicate.withPredicate(
                super.getListenTask(), TypesUtils.UNTIL_PRED_NAME, predicate, predClass);
        return this;
    }

    @Override
    protected FuncListenTaskBuilder self() {
        return this;
    }

    @Override
    protected FuncListenToBuilder newEventConsumptionStrategyBuilder() {
        return new FuncListenToBuilder(super.getListenTask());
    }
}
