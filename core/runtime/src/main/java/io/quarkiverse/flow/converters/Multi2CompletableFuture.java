package io.quarkiverse.flow.converters;

import java.util.concurrent.CompletableFuture;

import io.serverlessworkflow.impl.executors.func.DataTypeConverter;
import io.smallrye.mutiny.Multi;

@SuppressWarnings("rawtypes")
public class Multi2CompletableFuture implements DataTypeConverter<Multi, CompletableFuture> {

    @Override
    public CompletableFuture apply(Multi t) {
        return t.toUni().subscribeAsCompletionStage();
    }

    @Override
    public Class<Multi> sourceType() {
        return Multi.class;
    }
}
