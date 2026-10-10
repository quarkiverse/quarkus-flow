package io.quarkiverse.flow.tracing;

import jakarta.enterprise.inject.Instance;

public final class TraceCorrelationProviders {

    private TraceCorrelationProviders() {
    }

    public static TraceCorrelationProvider resolve(Instance<TraceCorrelationProvider> instance) {
        TraceCorrelationProvider provider = instance.isResolvable() ? instance.get() : TraceCorrelationProvider.NOOP;
        provider.registerConsumer();
        return provider;
    }
}
