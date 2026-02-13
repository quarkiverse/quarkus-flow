package io.quarkiverse.flow.converters;

import java.util.concurrent.CompletableFuture;

import io.serverlessworkflow.impl.executors.func.DataTypeConverter;
import io.smallrye.mutiny.Uni;

@SuppressWarnings("rawtypes")
public class Uni2CompletableFuture implements DataTypeConverter<Uni, CompletableFuture> {

    @Override
    public CompletableFuture apply(Uni t) {
        return t.subscribeAsCompletionStage();
    }

    @Override
    public Class<Uni> sourceType() {
        return Uni.class;
    }
}
