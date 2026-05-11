package io.quarkiverse.flow.opentelemetry.runtime;

import io.opentelemetry.api.trace.SpanBuilder;
import io.serverlessworkflow.api.types.TaskBase;

@FunctionalInterface
public interface TaskSpanEnricher<T extends TaskBase> {
    void enrich(SpanBuilder span, T task);
}
